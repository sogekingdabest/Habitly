import assert from "node:assert/strict";
import { after, before, beforeEach, describe, test } from "node:test";
import { readFile } from "node:fs/promises";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  arrayRemove,
  arrayUnion,
  deleteField,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  writeBatch,
} from "firebase/firestore";

const PROJECT_ID = "demo-habitly-rules";
const DAY_MS = 24 * 60 * 60 * 1000;
const OWNER = "owner";
const MEMBER = "member";
const GUEST = "guest";
const CODE = "ABC234";

let testEnv;

const profile = (name) => ({ displayName: name, nickname: name });

const household = (overrides = {}) => ({
  id: "house-1",
  name: "Casa demo",
  inviteCode: CODE,
  inviteCodeExpiresAt: Date.now() + 7 * DAY_MS,
  ownerId: OWNER,
  members: [OWNER, MEMBER],
  customStores: [],
  memberProfiles: {
    [OWNER]: profile("Owner"),
    [MEMBER]: profile("Member"),
  },
  joinProofs: {
    [MEMBER]: CODE,
  },
  ...overrides,
});

const mapping = (householdId, expiresAt) => ({
  householdId,
  createdAt: Date.now(),
  expiresAt,
});

async function seedHousehold(overrides = {}, { withMapping = true } = {}) {
  const data = household(overrides);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "households", data.id), data);
    if (withMapping) {
      await setDoc(
        doc(db, "invite_codes", data.inviteCode),
        mapping(data.id, data.inviteCodeExpiresAt),
      );
    }
  });
  return data;
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: await readFile("firestore.rules", "utf8"),
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  await testEnv.cleanup();
});

describe("invite code mappings", () => {
  test("an authenticated user can probe a missing code without listing codes", async () => {
    const db = testEnv.authenticatedContext(GUEST).firestore();
    const snapshot = await assertSucceeds(getDoc(doc(db, "invite_codes", "ZZZ999")));
    assert.equal(snapshot.exists(), false);
  });

  test("a household and its official mapping can be created atomically", async () => {
    const db = testEnv.authenticatedContext(OWNER).firestore();
    const expiresAt = Date.now() + 7 * DAY_MS;
    const data = household({
      members: [OWNER],
      memberProfiles: { [OWNER]: profile("Owner") },
      joinProofs: {},
      inviteCodeExpiresAt: expiresAt,
    });
    const batch = writeBatch(db);
    batch.set(doc(db, "households", data.id), data);
    batch.set(doc(db, "invite_codes", CODE), mapping(data.id, expiresAt));
    await assertSucceeds(batch.commit());
  });

  test("a member cannot mint an alternative or long-lived mapping", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    await assertFails(
      setDoc(
        doc(db, "invite_codes", "ZZZ999"),
        mapping(data.id, Date.now() + 365 * DAY_MS),
      ),
    );
  });

  test("the official mapping can only rotate atomically with the household", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    const newCode = "XYZ789";
    const expiresAt = Date.now() + 7 * DAY_MS;
    const batch = writeBatch(db);
    batch.update(doc(db, "households", data.id), {
      inviteCode: newCode,
      inviteCodeExpiresAt: expiresAt,
    });
    batch.set(doc(db, "invite_codes", newCode), mapping(data.id, expiresAt));
    batch.delete(doc(db, "invite_codes", data.inviteCode));
    await assertSucceeds(batch.commit());
  });
});

describe("joining a household", () => {
  test("knowing only the household id is not enough to rejoin", async () => {
    const data = await seedHousehold({
      members: [OWNER],
      memberProfiles: { [OWNER]: profile("Owner") },
      joinProofs: {},
    });
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    await assertFails(
      updateDoc(doc(db, "households", data.id), {
        members: arrayUnion(MEMBER),
        [`memberProfiles.${MEMBER}`]: profile("Member"),
      }),
    );
  });

  test("a wrong join proof is rejected", async () => {
    const data = await seedHousehold({
      members: [OWNER],
      memberProfiles: { [OWNER]: profile("Owner") },
      joinProofs: {},
    });
    const db = testEnv.authenticatedContext(GUEST).firestore();
    await assertFails(
      updateDoc(doc(db, "households", data.id), {
        members: arrayUnion(GUEST),
        [`memberProfiles.${GUEST}`]: profile("Guest"),
        [`joinProofs.${GUEST}`]: "WRONG2",
      }),
    );
  });

  test("an expired invite proof is rejected", async () => {
    const data = await seedHousehold({
      members: [OWNER],
      memberProfiles: { [OWNER]: profile("Owner") },
      joinProofs: {},
      inviteCodeExpiresAt: Date.now() - DAY_MS,
    }, { withMapping: false });
    const db = testEnv.authenticatedContext(GUEST).firestore();
    await assertFails(
      updateDoc(doc(db, "households", data.id), {
        members: arrayUnion(GUEST),
        [`memberProfiles.${GUEST}`]: profile("Guest"),
        [`joinProofs.${GUEST}`]: CODE,
      }),
    );
  });

  test("a current, explicit invite proof allows joining", async () => {
    const data = await seedHousehold({
      members: [OWNER],
      memberProfiles: { [OWNER]: profile("Owner") },
      joinProofs: {},
    });
    const db = testEnv.authenticatedContext(GUEST).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "households", data.id), {
        members: arrayUnion(GUEST),
        [`memberProfiles.${GUEST}`]: profile("Guest"),
        [`joinProofs.${GUEST}`]: CODE,
      }),
    );
  });
});

describe("member updates and removal", () => {
  test("a member can update their own profile but not another member's", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "households", data.id), {
        [`memberProfiles.${MEMBER}`]: profile("My new name"),
      }),
    );
    await assertFails(
      updateDoc(doc(db, "households", data.id), {
        [`memberProfiles.${OWNER}`]: profile("Impersonated"),
      }),
    );
  });

  test("ordinary household fields remain editable by members", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    await assertSucceeds(
      updateDoc(doc(db, "households", data.id), {
        name: "Nuevo nombre",
        customStores: ["Mercado local"],
      }),
    );
  });

  test("removal without proof deletion and code rotation is rejected", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(OWNER).firestore();
    await assertFails(
      updateDoc(doc(db, "households", data.id), {
        members: arrayRemove(MEMBER),
        [`memberProfiles.${MEMBER}`]: deleteField(),
      }),
    );
  });

  test("the owner can remove a member only with atomic code rotation", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(OWNER).firestore();
    const newCode = "NEW456";
    const expiresAt = Date.now() + 7 * DAY_MS;
    const batch = writeBatch(db);
    batch.update(doc(db, "households", data.id), {
      members: arrayRemove(MEMBER),
      [`memberProfiles.${MEMBER}`]: deleteField(),
      [`joinProofs.${MEMBER}`]: deleteField(),
      inviteCode: newCode,
      inviteCodeExpiresAt: expiresAt,
    });
    batch.set(doc(db, "invite_codes", newCode), mapping(data.id, expiresAt));
    batch.delete(doc(db, "invite_codes", data.inviteCode));
    await assertSucceeds(batch.commit());
  });

  test("a member can leave only while rotating the code for those who remain", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    const newCode = "NXT456";
    const expiresAt = Date.now() + 7 * DAY_MS;
    const batch = writeBatch(db);
    batch.update(doc(db, "households", data.id), {
      members: arrayRemove(MEMBER),
      [`memberProfiles.${MEMBER}`]: deleteField(),
      [`joinProofs.${MEMBER}`]: deleteField(),
      inviteCode: newCode,
      inviteCodeExpiresAt: expiresAt,
    });
    batch.set(doc(db, "invite_codes", newCode), mapping(data.id, expiresAt));
    batch.delete(doc(db, "invite_codes", data.inviteCode));
    await assertSucceeds(batch.commit());
  });

  test("ownership transfers atomically when the owner leaves", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(OWNER).firestore();
    const newCode = "HER456";
    const expiresAt = Date.now() + 7 * DAY_MS;
    const batch = writeBatch(db);
    batch.update(doc(db, "households", data.id), {
      members: arrayRemove(OWNER),
      [`memberProfiles.${OWNER}`]: deleteField(),
      [`joinProofs.${OWNER}`]: deleteField(),
      ownerId: MEMBER,
      inviteCode: newCode,
      inviteCodeExpiresAt: expiresAt,
    });
    batch.set(doc(db, "invite_codes", newCode), mapping(data.id, expiresAt));
    batch.delete(doc(db, "invite_codes", data.inviteCode));
    await assertSucceeds(batch.commit());
  });

  test("the sole owner must delete the household and mapping atomically", async () => {
    const data = await seedHousehold({
      members: [OWNER],
      memberProfiles: { [OWNER]: profile("Owner") },
      joinProofs: {},
    });
    const db = testEnv.authenticatedContext(OWNER).firestore();
    await assertFails(
      updateDoc(doc(db, "households", data.id), {
        members: arrayRemove(OWNER),
        [`memberProfiles.${OWNER}`]: deleteField(),
        [`joinProofs.${OWNER}`]: deleteField(),
      }),
    );
    const batch = writeBatch(db);
    batch.delete(doc(db, "households", data.id));
    batch.delete(doc(db, "invite_codes", data.inviteCode));
    await assertSucceeds(batch.commit());
  });

  test("a non-owner cannot remove another member", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    const newCode = "NEW456";
    const expiresAt = Date.now() + 7 * DAY_MS;
    const batch = writeBatch(db);
    batch.update(doc(db, "households", data.id), {
      members: arrayRemove(OWNER),
      [`memberProfiles.${OWNER}`]: deleteField(),
      inviteCode: newCode,
      inviteCodeExpiresAt: expiresAt,
    });
    batch.set(doc(db, "invite_codes", newCode), mapping(data.id, expiresAt));
    batch.delete(doc(db, "invite_codes", data.inviteCode));
    await assertFails(batch.commit());
  });
});

describe("household subcollections", () => {
  test("members retain access to direct and nested household data", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    await assertSucceeds(
      setDoc(doc(db, "households", data.id, "shopping_items", "item-1"), {
        name: "Leche",
      }),
    );
    await assertSucceeds(
      setDoc(
        doc(db, "households", data.id, "routines", "routine-1", "completions", "day-1"),
        { completed: true },
      ),
    );
  });

  test("non-members cannot access household subcollections", async () => {
    const data = await seedHousehold();
    const db = testEnv.authenticatedContext(GUEST).firestore();
    await assertFails(
      setDoc(doc(db, "households", data.id, "shopping_items", "item-1"), {
        name: "Leche",
      }),
    );
  });
});
