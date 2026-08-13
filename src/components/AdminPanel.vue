<script setup lang="ts">
import { reactive, ref } from "vue";
import { api, invokeError, type UserDetails, type UserQuota } from "../lib/ipc";
import { formatBytes } from "../lib/format";

const search = ref("");
const users = ref<string[]>([]);
const selected = ref<UserDetails | null>(null);
const loading = ref(false);
const detailsLoading = ref(false);
const error = ref<string | null>(null);
const editMsg = ref<string | null>(null);
const showPassword = ref(false);

const edits = reactive({
  displayName: "",
  email: "",
  password: "",
  quota: null as number | null,
});

async function listUsers() {
  loading.value = true;
  error.value = null;
  editMsg.value = null;
  selected.value = null;
  try {
    users.value = await api.adminListUsers(search.value.trim());
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    loading.value = false;
  }
}

async function selectUser(userId: string) {
  detailsLoading.value = true;
  error.value = null;
  editMsg.value = null;
  try {
    selected.value = await api.adminGetUser(userId);
    edits.displayName = selected.value.displayName ?? "";
    edits.email = selected.value.email ?? "";
    edits.password = "";
    edits.quota = selected.value.quota?.total ?? null;
  } catch (e) {
    error.value = invokeError(e).message;
  } finally {
    detailsLoading.value = false;
  }
}

async function saveField(key: "displayname" | "email" | "password") {
  if (!selected.value) return;
  const value = edits[key === "displayname" ? "displayName" : key];
  error.value = null;
  editMsg.value = null;
  if (!value) {
    if (key === "password") {
      edits.password = "";
      editMsg.value = "Password unchanged.";
      return;
    }
    error.value = key === "displayname" ? "Display name cannot be empty." : "Email cannot be empty.";
    return;
  }
  try {
    editMsg.value = await api.adminEditUser(selected.value.id, key, value);
    if (key === "displayname" || key === "email") {
      selected.value = await api.adminGetUser(selected.value.id);
      edits.displayName = selected.value.displayName ?? "";
      edits.email = selected.value.email ?? "";
    } else {
      edits.password = "";
    }
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

async function setQuota() {
  if (!selected.value || edits.quota === null) return;
  error.value = null;
  editMsg.value = null;
  try {
    editMsg.value = await api.adminSetUserQuota(selected.value.id, edits.quota);
    await selectUser(selected.value.id);
  } catch (e) {
    error.value = invokeError(e).message;
  }
}

function quotaTotal(q: UserQuota | null): string {
  return q?.total === null ? "unlimited" : formatBytes(q?.total ?? null);
}

function quotaUsed(q: UserQuota | null): string {
  return formatBytes(q?.used ?? null);
}

function quotaFree(q: UserQuota | null): string {
  if (!q || q.total === null) return "unlimited";
  if (q.used === null) return formatBytes(q.total);
  const free = q.total - q.used;
  if (free < 0) return `over by ${formatBytes(-free)}`;
  return formatBytes(free);
}
</script>

<template>
  <div class="flex h-full flex-col gap-4 p-6">
    <div>
      <h2 class="text-lg font-semibold text-white">Admin Panel</h2>
      <p class="text-sm text-zinc-500">
        User provisioning via the Nextcloud OCS API. Only available for admin accounts.
      </p>
    </div>

    <div v-if="error" class="rounded-md border border-red-800 bg-red-950/50 px-3 py-2 text-sm text-red-300">
      {{ error }}
    </div>
    <div v-if="editMsg" class="rounded-md border border-emerald-800 bg-emerald-950/50 px-3 py-2 text-sm text-emerald-300">
      {{ editMsg }}
    </div>

    <div class="flex gap-2">
      <input
        v-model="search"
        class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
        placeholder="Search users…"
        @keyup.enter="listUsers"
      />
      <button
        class="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-500"
        @click="listUsers"
      >
        {{ loading ? "Loading…" : "List users" }}
      </button>
    </div>

    <div class="grid flex-1 grid-cols-2 gap-4 overflow-hidden">
      <div class="overflow-y-auto rounded-lg border border-zinc-800">
        <ul v-if="users.length" class="divide-y divide-zinc-800/60">
          <li v-for="userId in users" :key="userId">
            <button
              class="w-full px-4 py-2 text-left text-sm text-zinc-200 hover:bg-zinc-800/60"
              :class="selected?.id === userId ? 'bg-indigo-950/60 text-indigo-200' : ''"
              @click="selectUser(userId)"
            >
              {{ userId }}
            </button>
          </li>
        </ul>
        <p v-else class="p-4 text-sm text-zinc-500">
          {{ loading ? "Loading…" : "No users listed yet. Run a search to get started." }}
        </p>
      </div>

      <div class="overflow-y-auto rounded-lg border border-zinc-800 p-4">
        <p v-if="detailsLoading" class="text-sm text-zinc-500">Loading details…</p>
        <template v-else-if="selected">
          <h3 class="text-base font-medium text-white">{{ selected.displayName || selected.id }}</h3>
          <p class="text-sm text-zinc-500">{{ selected.id }}</p>

          <div class="mt-4 space-y-3">
            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                Display name
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.displayName"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white focus:border-indigo-500 focus:outline-none"
                />
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-white hover:bg-zinc-600"
                  @click="saveField('displayname')"
                >
                  Save
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                Email
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.email"
                  type="email"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white focus:border-indigo-500 focus:outline-none"
                />
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-white hover:bg-zinc-600"
                  @click="saveField('email')"
                >
                  Save
                </button>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                Password
              </label>
              <div class="flex gap-2">
                <input
                  v-model="edits.password"
                  :type="showPassword ? 'text' : 'password'"
                  placeholder="Leave empty to keep current password"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white placeholder-zinc-500 focus:border-indigo-500 focus:outline-none"
                />
                <button
                  class="rounded-md bg-zinc-800 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-700"
                  @click="showPassword = !showPassword"
                >
                  {{ showPassword ? "Hide" : "Show" }}
                </button>
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-white hover:bg-zinc-600"
                  @click="saveField('password')"
                >
                  Set
                </button>
              </div>
            </div>

            <div class="rounded-md bg-zinc-800/60 p-3 text-sm text-zinc-300">
              <div class="flex justify-between">
                <span class="text-zinc-500">Quota</span>
                <span>{{ quotaTotal(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-zinc-500">Used</span>
                <span>{{ quotaUsed(selected.quota) }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-zinc-500">Free</span>
                <span>{{ quotaFree(selected.quota) }}</span>
              </div>
            </div>

            <div>
              <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-zinc-500">
                Set quota (bytes, -3 = unlimited)
              </label>
              <div class="flex gap-2">
                <input
                  v-model.number="edits.quota"
                  type="number"
                  class="flex-1 rounded-md border border-zinc-700 bg-zinc-800 px-3 py-2 text-sm text-white focus:border-indigo-500 focus:outline-none"
                />
                <button
                  class="rounded-md bg-zinc-700 px-3 py-2 text-sm text-white hover:bg-zinc-600"
                  @click="setQuota"
                >
                  Save
                </button>
              </div>
            </div>
          </div>
        </template>
        <p v-else class="text-sm text-zinc-500">Select a user to view details.</p>
      </div>
    </div>
  </div>
</template>
