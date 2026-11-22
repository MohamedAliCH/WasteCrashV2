const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const SPECTATOR_UID = "spectator_uid_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function validUserProfile(uid) {
  const now = pastTime();
  return {
    userId: uid,
    displayName: "TunisiaEcoHero",
    friendCode: "TN2026",
    trophies: 120,
    ecoCoins: 450,
    wins: 4,
    losses: 1,
    equippedDeckIds: ["boss_b1", "boss_b2", "boss_b3"],
    bossLevels: { boss_b1: 1, boss_b2: 2, boss_b3: 1 },
    bossCopies: { boss_b1: 2, boss_b2: 1, boss_b3: 0 },
    friendUids: [BOB_UID],
    dailyQuestSeedDate: "2026-11-21",
    questProgress: { q_blue_100: 45, q_epic_3: 1 },
    questClaimed: { q_blue_100: false, q_epic_3: false },
    createdAt: now,
    updatedAt: now,
  };
}

function validPlayerLiveState(uid, name) {
  return {
    uid,
    name,
    trophies: 210,
    deckBossIds: ["boss_b1", "boss_b2", "boss_b3"],
    deckBossLevels: [1, 2, 1],
    waveIndex: 1,
    bossHp: 1200,
    bossMaxHp: 1500,
    totalDamage: 850,
    restorationPct: 42,
    boardFlat: Array(49).fill(0),
    skillCharges: [50, 100, 20],
    lastAbility: "Tidal Plastic Sweep",
  };
}

function validLiveMatch(creatorUid, opponentUid) {
  const now = pastTime();
  return {
    creatorUid,
    participantUids: [creatorUid, opponentUid],
    arenaId: 0,
    placeId: "place_hammamet",
    bossId: "boss_b1",
    trophyBracket: 0,
    status: "ACTIVE",
    durationSec: 90,
    spectatorCount: 1,
    botMatch: false,
    winnerUid: "",
    player1: validPlayerLiveState(creatorUid, "Alice"),
    player2: validPlayerLiveState(opponentUid, "Bob"),
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated requests are denied across all collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("publicProfiles").where("publicVisible", "==", true).get());
  await assertFails(db.collection("liveMatches").where("status", "==", "ACTIVE").get());
  await assertFails(db.collection("replays").where("replayPublic", "==", true).get());
});

test("Users collection: owner create/read/partial update succeeds, cross-user & ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      trophies: 150,
      ecoCoins: 520,
      updatedAt: pastTime(1000),
    })
  );

  // Cross-user read/write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ ecoCoins: 999999, updatedAt: pastTime(500) }));

  // Economy jumps beyond one match / quest are blocked
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ ecoCoins: 999999, updatedAt: pastTime(400) }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ trophies: 5000, updatedAt: pastTime(400) }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ wins: 40, updatedAt: pastTime(400) }));

  // Immutable field & ghost field blocked
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ friendCode: "HACK99", updatedAt: pastTime(500) }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({ isAdmin: true, updatedAt: pastTime(500) }));
  await assertFails(aliceDb.collection("users").get());
});

test("PublicProfiles: filtered query succeeds, unfiltered fails, owner-only write", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID)));
  const pub = {
    userId: ALICE_UID,
    displayName: "AliceTN",
    friendCode: "TN2026",
    trophies: 120,
    arenaId: 0,
    wins: 4,
    equippedDeckIds: ["boss_b1", "boss_b2", "boss_b3"],
    publicVisible: true,
    updatedAt: pastTime(),
  };

  // Public numbers must mirror the private profile
  await assertFails(aliceDb.collection("publicProfiles").doc(ALICE_UID).set({ ...pub, trophies: 9999 }));
  await assertSucceeds(aliceDb.collection("publicProfiles").doc(ALICE_UID).set(pub));
  await assertFails(bobDb.collection("publicProfiles").doc(ALICE_UID).update({ trophies: 9999, updatedAt: pastTime(1000) }));
  await assertSucceeds(bobDb.collection("publicProfiles").where("publicVisible", "==", true).where("friendCode", "==", "TN2026").get());
  await assertFails(bobDb.collection("publicProfiles").get());
});

test("MatchmakingQueue: owner creates, opponent matches SEARCHING ticket, unfiltered list blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastTime();

  await assertSucceeds(
    aliceDb.collection("matchmakingQueue").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      trophies: 180,
      arenaId: 0,
      deckBossIds: ["boss_b1", "boss_b2", "boss_b3"],
      deckBossLevels: [1, 1, 1],
      status: "SEARCHING",
      matchId: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(bobDb.collection("matchmakingQueue").where("status", "==", "SEARCHING").get());
  await assertFails(bobDb.collection("matchmakingQueue").get());

  // A bare claim (no live match created with it) is rejected
  await assertFails(
    bobDb.collection("matchmakingQueue").doc(ALICE_UID).update({
      status: "MATCHED",
      matchId: "match_ghost",
      updatedAt: pastTime(1000),
    })
  );

  // Opponent claims SEARCHING ticket atomically with the live match that contains both players
  const batch = bobDb.batch();
  batch.set(bobDb.collection("liveMatches").doc("match_123"), validLiveMatch(BOB_UID, ALICE_UID));
  batch.update(bobDb.collection("matchmakingQueue").doc(ALICE_UID), {
    status: "MATCHED",
    matchId: "match_123",
    updatedAt: pastTime(1000),
  });
  await assertSucceeds(batch.commit());

  // Cannot re-claim MATCHED ticket
  await assertFails(
    bobDb.collection("matchmakingQueue").doc(ALICE_UID).update({
      status: "MATCHED",
      matchId: "match_hijack",
      updatedAt: pastTime(500),
    })
  );
});

test("LiveMatches: participants stream state, spectators read & update spectatorCount only, terminal lock enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const specDb = testEnv.authenticatedContext(SPECTATOR_UID).firestore();

  await assertSucceeds(aliceDb.collection("liveMatches").doc("match_1").set(validLiveMatch(ALICE_UID, BOB_UID)));

  // Live spectator filtered list succeeds; unfiltered fails
  await assertSucceeds(specDb.collection("liveMatches").where("status", "==", "ACTIVE").get());
  await assertFails(specDb.collection("liveMatches").get());

  // Spectator can update spectatorCount only, NOT gameplay state
  await assertSucceeds(specDb.collection("liveMatches").doc("match_1").update({ spectatorCount: 2 }));
  await assertFails(specDb.collection("liveMatches").doc("match_1").update({ winnerUid: SPECTATOR_UID, updatedAt: pastTime(500) }));

  // A participant cannot overwrite the opponent's state or crown an outsider
  await assertFails(
    bobDb.collection("liveMatches").doc("match_1").update({
      player1: { ...validPlayerLiveState(ALICE_UID, "Alice"), bossHp: 1500, totalDamage: 0 },
      updatedAt: pastTime(600),
    })
  );
  await assertFails(
    bobDb.collection("liveMatches").doc("match_1").update({
      status: "FINISHED",
      winnerUid: SPECTATOR_UID,
      updatedAt: pastTime(600),
    })
  );
  // Spectator count may only move by one
  await assertFails(specDb.collection("liveMatches").doc("match_1").update({ spectatorCount: 500 }));

  // Participant can finish match, then terminal state locks further updates
  await assertSucceeds(
    bobDb.collection("liveMatches").doc("match_1").update({
      status: "FINISHED",
      winnerUid: BOB_UID,
      updatedAt: pastTime(500),
    })
  );
  await assertFails(
    aliceDb.collection("liveMatches").doc("match_1").update({
      winnerUid: ALICE_UID,
      updatedAt: pastTime(200),
    })
  );
});

test("Replays: participant creates immutable replay, friends query replayPublic==true, updates denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const specDb = testEnv.authenticatedContext(SPECTATOR_UID).firestore();

  const replayDoc = {
    creatorUid: ALICE_UID,
    participantUids: [ALICE_UID, BOB_UID],
    replayPublic: true,
    arenaId: 0,
    placeId: "place_sidi_bou_said",
    bossId: "boss_b2",
    trophyBracket: 0,
    winnerUid: ALICE_UID,
    durationSec: 74,
    player1Summary: {
      uid: ALICE_UID,
      name: "Alice",
      trophies: 220,
      deckBossIds: ["boss_b1", "boss_b2", "boss_b3"],
      deckBossLevels: [2, 1, 1],
      totalDamage: 2400,
      restorationPct: 100,
    },
    player2Summary: {
      uid: BOB_UID,
      name: "Bob",
      trophies: 215,
      deckBossIds: ["boss_b2", "boss_b3", "boss_b4"],
      deckBossLevels: [1, 1, 1],
      totalDamage: 1850,
      restorationPct: 77,
    },
    frames: [
      {
        elapsedSec: 10,
        p1Board: Array(49).fill(1),
        p2Board: Array(49).fill(2),
        p1BossHp: 1200,
        p2BossHp: 1350,
        p1Wave: 2,
        p2Wave: 1,
        p1Damage: 600,
        p2Damage: 420,
        p1Restoration: 35,
        p2Restoration: 24,
        p1Charges: [80, 40, 20],
        p2Charges: [30, 50, 10],
        eventText: "Alice cleared Wave 1!",
      },
    ],
    createdAt: pastTime(),
  };

  await assertSucceeds(aliceDb.collection("replays").doc("replay_1").set(replayDoc));
  await assertSucceeds(specDb.collection("replays").where("replayPublic", "==", true).get());
  await assertFails(specDb.collection("replays").get());
  await assertFails(aliceDb.collection("replays").doc("replay_1").update({ winnerUid: BOB_UID }));
});
