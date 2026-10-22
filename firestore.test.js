const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.doc(`users/${ALICE_UID}`).get());
});

test("Unauthenticated user: cannot read projects", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/projects`).get());
});

test("Alice: can create and read her own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      displayName: "Alice Developer",
      email: "alice@example.com",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}`).get());
});

test("Alice: can create and list her own projects", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).set({
      id: "proj_1",
      userId: ALICE_UID,
      name: "Ecommerce Store",
      targetPlatform: "android",
      status: "planning",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/projects`).get());
});

test("Bob: cannot read Alice's projects (User Isolation)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/projects/proj_1`).set({
      id: "proj_1",
      userId: ALICE_UID,
      name: "Secret Project",
      targetPlatform: "android",
      status: "planning",
      createdAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/projects/proj_1`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/projects`).get());
});

test("Bob: cannot write into Alice's api_connections (Credential Isolation)", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/api_connections/conn_1`).set({
      id: "conn_1",
      userId: BOB_UID,
      name: "Stolen Key",
      provider: "openai",
      maskedKey: "sk-...",
      status: "active",
      createdAt: new Date(),
    })
  );
});

test("Alice: can save and read checkpoints for project continuity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/projects/proj_1/checkpoints/chk_1`).set({
      id: "chk_1",
      userId: ALICE_UID,
      projectId: "proj_1",
      version: 1,
      title: "Checkpoint 1: Setup Completed",
      contextSummary: "Initial project scaffold created.",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.doc(`users/${ALICE_UID}/projects/proj_1/checkpoints/chk_1`).get());
});
