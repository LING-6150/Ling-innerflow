<template>
  <div class="min-h-screen bg-white text-slate-900">
    <!-- Top bar -->
    <div class="sticky top-0 z-20 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div class="mx-auto flex max-w-7xl items-center justify-between px-4 py-3">
        <div class="flex items-center gap-3">
          <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-600 text-white shadow-sm">
            <span class="text-sm font-semibold">MD</span>
          </div>
          <div>
            <div class="text-sm font-semibold text-slate-900">Doctor Dashboard</div>
            <div class="text-xs text-slate-500">Patient monitoring and clinical summary</div>
          </div>
        </div>

        <div class="hidden items-center gap-2 md:flex">
          <div class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-600">
            Primary: <span class="font-semibold text-blue-600">#2563eb</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <div class="pointer-events-none fixed right-4 top-16 z-50">
      <transition name="fade">
        <div
          v-if="toast.open"
          class="pointer-events-auto w-[320px] rounded-xl border px-4 py-3 shadow-lg"
          :class="toast.type === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-900' : 'border-rose-200 bg-rose-50 text-rose-900'"
        >
          <div class="flex items-start gap-3">
            <div
              class="mt-0.5 flex h-6 w-6 items-center justify-center rounded-lg text-xs font-bold"
              :class="toast.type === 'success' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'"
            >
              {{ toast.type === 'success' ? 'OK' : '!' }}
            </div>
            <div class="min-w-0">
              <div class="text-sm font-semibold">
                {{ toast.title }}
              </div>
              <div v-if="toast.message" class="mt-0.5 text-xs opacity-90">
                {{ toast.message }}
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>

    <div class="mx-auto max-w-7xl px-4 py-4">
      <div class="flex gap-4">
        <!-- Sidebar (desktop) -->
        <aside class="hidden w-[320px] shrink-0 md:block">
          <div class="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-4 py-3">
              <div class="text-sm font-semibold text-slate-900">Patients</div>
              <div class="mt-0.5 text-xs text-slate-500">Prioritized by risk and last active</div>
            </div>

            <div class="max-h-[calc(100vh-170px)] overflow-auto">
              <div v-if="patientsLoading" class="p-3">
                <div v-for="i in 7" :key="i" class="mb-2 animate-pulse rounded-xl border border-slate-100 p-3">
                  <div class="flex items-center justify-between">
                    <div class="h-4 w-24 rounded bg-slate-100"></div>
                    <div class="h-5 w-12 rounded bg-slate-100"></div>
                  </div>
                  <div class="mt-2 h-3 w-32 rounded bg-slate-100"></div>
                </div>
              </div>

              <div v-else-if="patientsError" class="p-4">
                <div class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
                  Failed to load patients.
                  <div class="mt-3">
                    <button
                      class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-blue-700"
                      @click="loadPatients"
                    >
                      Retry
                    </button>
                  </div>
                </div>
              </div>

              <div v-else class="p-2">
                <button
                  v-for="p in sortedPatients"
                  :key="p.userId"
                  class="group w-full rounded-xl p-3 text-left transition"
                  :class="selectedUserId === p.userId ? 'bg-blue-50 ring-1 ring-blue-200' : 'hover:bg-slate-50'"
                  @click="selectPatient(p.userId)"
                >
                  <div class="flex items-center justify-between gap-2">
                    <div class="min-w-0">
                      <div class="flex items-center gap-2">
                        <span
                          v-if="p.latestEmotionLevel === 'L5'"
                          class="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-700"
                        >
                          <span aria-hidden="true">⚠️</span>
                          High Risk
                        </span>
                        <span class="truncate text-sm font-semibold text-slate-900">Patient {{ p.userId }}</span>
                      </div>
                    </div>
                    <span
                      class="shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold"
                      :class="levelPillClass(p.latestEmotionLevel)"
                    >
                      {{ p.latestEmotionLevel || '—' }}
                    </span>
                  </div>
                  <div class="mt-2 flex items-center justify-between text-xs text-slate-500">
                    <span>Last active</span>
                    <span class="font-medium text-slate-700">{{ formatRelativeTime(p.lastActiveAt) }}</span>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </aside>

        <!-- Mobile: list -->
        <div v-if="isMobile && mobilePane === 'list'" class="w-full md:hidden">
          <div class="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-4 py-3">
              <div class="text-sm font-semibold text-slate-900">Patients</div>
              <div class="mt-0.5 text-xs text-slate-500">Tap a patient to view details</div>
            </div>

            <div class="max-h-[calc(100vh-170px)] overflow-auto p-2">
              <div v-if="patientsLoading" class="p-1">
                <div v-for="i in 7" :key="i" class="mb-2 animate-pulse rounded-xl border border-slate-100 p-3">
                  <div class="flex items-center justify-between">
                    <div class="h-4 w-24 rounded bg-slate-100"></div>
                    <div class="h-5 w-12 rounded bg-slate-100"></div>
                  </div>
                  <div class="mt-2 h-3 w-32 rounded bg-slate-100"></div>
                </div>
              </div>

              <div v-else-if="patientsError" class="p-3">
                <div class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
                  Failed to load patients.
                  <div class="mt-3">
                    <button
                      class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-blue-700"
                      @click="loadPatients"
                    >
                      Retry
                    </button>
                  </div>
                </div>
              </div>

              <div v-else>
                <button
                  v-for="p in sortedPatients"
                  :key="p.userId"
                  class="group w-full rounded-xl p-3 text-left transition hover:bg-slate-50"
                  @click="selectPatient(p.userId, true)"
                >
                  <div class="flex items-center justify-between gap-2">
                    <div class="min-w-0">
                      <div class="flex items-center gap-2">
                        <span
                          v-if="p.latestEmotionLevel === 'L5'"
                          class="inline-flex items-center gap-1 rounded-full bg-rose-50 px-2 py-0.5 text-xs font-semibold text-rose-700"
                        >
                          <span aria-hidden="true">⚠️</span>
                          High Risk
                        </span>
                        <span class="truncate text-sm font-semibold text-slate-900">Patient {{ p.userId }}</span>
                      </div>
                    </div>
                    <span class="shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold" :class="levelPillClass(p.latestEmotionLevel)">
                      {{ p.latestEmotionLevel || '—' }}
                    </span>
                  </div>
                  <div class="mt-2 flex items-center justify-between text-xs text-slate-500">
                    <span>Last active</span>
                    <span class="font-medium text-slate-700">{{ formatRelativeTime(p.lastActiveAt) }}</span>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Detail panel -->
        <main class="min-w-0 flex-1">
          <div class="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="flex items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <button
                    v-if="isMobile && mobilePane === 'detail'"
                    class="inline-flex items-center justify-center rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
                    @click="mobilePane = 'list'"
                  >
                    ← Back
                  </button>
                  <div class="truncate text-sm font-semibold text-slate-900">
                    Patient {{ selectedUserId || '—' }}
                  </div>
                  <span
                    v-if="selectedPatient?.latestEmotionLevel"
                    class="rounded-full px-2 py-0.5 text-xs font-semibold"
                    :class="levelPillClass(selectedPatient.latestEmotionLevel)"
                  >
                    {{ selectedPatient.latestEmotionLevel }}
                  </span>
                </div>
                <div class="mt-0.5 text-xs text-slate-500">
                  Last active: <span class="font-medium text-slate-700">{{ formatRelativeTime(selectedPatient?.lastActiveAt) }}</span>
                </div>
              </div>

              <button
                class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="!selectedUserId || fhirLoading"
                @click="generateFhirReport"
              >
                <span v-if="!fhirLoading">Generate FHIR Report</span>
                <span v-else>Generating…</span>
              </button>
            </div>

            <div class="p-4">
              <div v-if="!selectedUserId" class="rounded-xl border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-600">
                Select a patient to view details.
              </div>

              <div v-else-if="detailLoading" class="space-y-4">
                <div class="animate-pulse rounded-2xl border border-slate-100 p-4">
                  <div class="h-4 w-40 rounded bg-slate-100"></div>
                  <div class="mt-4 h-44 w-full rounded bg-slate-100"></div>
                </div>
                <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
                  <div class="animate-pulse rounded-2xl border border-slate-100 p-4">
                    <div class="h-4 w-32 rounded bg-slate-100"></div>
                    <div class="mt-3 h-8 w-24 rounded bg-slate-100"></div>
                    <div class="mt-2 h-3 w-40 rounded bg-slate-100"></div>
                  </div>
                  <div class="animate-pulse rounded-2xl border border-slate-100 p-4 lg:col-span-2">
                    <div class="h-4 w-40 rounded bg-slate-100"></div>
                    <div class="mt-3 h-20 w-full rounded bg-slate-100"></div>
                  </div>
                </div>
                <div class="animate-pulse rounded-2xl border border-slate-100 p-4">
                  <div class="h-4 w-36 rounded bg-slate-100"></div>
                  <div class="mt-3 space-y-2">
                    <div class="h-10 w-full rounded bg-slate-100"></div>
                    <div class="h-10 w-full rounded bg-slate-100"></div>
                    <div class="h-10 w-full rounded bg-slate-100"></div>
                  </div>
                </div>
              </div>

              <div v-else-if="detailError" class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
                Failed to load patient details.
                <div class="mt-3">
                  <button
                    class="inline-flex items-center justify-center rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-blue-700"
                    @click="loadPatientDetails(selectedUserId)"
                  >
                    Retry
                  </button>
                </div>
              </div>

              <div v-else class="space-y-4">
                <!-- Trend chart -->
                <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div class="flex items-center justify-between">
                    <div class="text-sm font-semibold text-slate-900">7-Day Emotion Trend</div>
                    <div class="text-xs text-slate-500">L1–L5 mapped to 1–5</div>
                  </div>
                  <div class="mt-3 h-[260px] w-full">
                    <canvas ref="chartCanvas" class="h-full w-full"></canvas>
                  </div>
                </div>

                <!-- Summary cards -->
                <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
                  <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                    <div class="text-sm font-semibold text-slate-900">PHQ-9</div>
                    <div class="mt-2 flex items-end gap-2">
                      <div class="text-3xl font-semibold text-slate-900">
                        {{ summary?.phq9Score ?? '—' }}
                      </div>
                      <div class="pb-1 text-sm font-semibold text-blue-600">
                        {{ summary?.phq9Severity || '—' }}
                      </div>
                    </div>
                    <div class="mt-2 text-xs text-slate-500">Patient Health Questionnaire (9 items)</div>
                  </div>

                  <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm lg:col-span-2">
                    <div class="text-sm font-semibold text-slate-900">Clinical Reflection (L4)</div>
                    <div class="mt-2 whitespace-pre-wrap rounded-xl border border-slate-100 bg-slate-50 p-3 text-sm text-slate-700">
                      {{ summary?.reflection || 'No reflection available.' }}
                    </div>
                  </div>
                </div>

                <!-- Crisis alerts -->
                <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div class="flex items-center justify-between gap-2">
                    <div class="text-sm font-semibold text-slate-900">Crisis Alerts</div>
                    <div class="text-xs text-slate-500">{{ crisisAlerts.length }} record(s)</div>
                  </div>

                  <div v-if="crisisAlerts.length === 0" class="mt-3 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
                    No crisis records.
                  </div>

                  <div v-else class="mt-3 space-y-2">
                    <div
                      v-for="(a, idx) in crisisAlerts"
                      :key="idx"
                      class="rounded-xl border p-3"
                      :class="a.level === 'L5' ? 'border-rose-200 bg-rose-50' : 'border-slate-200 bg-white'"
                    >
                      <div class="flex items-center justify-between gap-2">
                        <div class="text-xs font-semibold text-slate-900">
                          {{ formatDateTime(a.time) }}
                        </div>
                        <span class="rounded-full px-2 py-0.5 text-xs font-semibold" :class="levelPillClass(a.level)">
                          {{ a.level }}
                        </span>
                      </div>
                      <div class="mt-2 text-sm text-slate-700">
                        {{ a.content }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Chart from 'chart.js/auto'
import request from '@/api/request'

type EmotionLevel = 'L1' | 'L2' | 'L3' | 'L4' | 'L5'

type PatientItem = {
  userId: string | number
  latestEmotionLevel?: EmotionLevel
  lastActiveAt?: string | number
}

type TrendPoint = {
  date: string
  level: EmotionLevel | number | string
}

type PatientSummary = {
  phq9Score?: number
  phq9Severity?: string
  reflection?: string
}

type CrisisAlert = {
  time: string
  content: string
  level: EmotionLevel
}

const isMobile = ref(false)
const mobilePane = ref<'list' | 'detail'>('detail')

function updateIsMobile() {
  isMobile.value = window.matchMedia('(max-width: 767px)').matches
  if (isMobile.value && mobilePane.value !== 'list' && !selectedUserId.value) {
    mobilePane.value = 'list'
  }
}

// --- Toast ---
const toast = ref<{ open: boolean; type: 'success' | 'error'; title: string; message?: string }>({
  open: false,
  type: 'success',
  title: ''
})
let toastTimer: number | undefined
function showToast(type: 'success' | 'error', title: string, message?: string) {
  toast.value = { open: true, type, title, message }
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => {
    toast.value.open = false
  }, 3200)
}

// --- Patients list ---
const patients = ref<PatientItem[]>([])
const patientsLoading = ref(false)
const patientsError = ref<string | null>(null)

const selectedUserId = ref<string | null>(null)
const selectedPatient = computed(() => patients.value.find((p) => String(p.userId) === String(selectedUserId.value)) || null)

function toEpochMs(v?: string | number): number {
  if (v == null) return 0
  if (typeof v === 'number') return v > 10_000_000_000 ? v : v * 1000
  const t = Date.parse(v)
  return Number.isFinite(t) ? t : 0
}

const sortedPatients = computed(() => {
  const list = [...patients.value]
  list.sort((a, b) => {
    const aL5 = a.latestEmotionLevel === 'L5'
    const bL5 = b.latestEmotionLevel === 'L5'
    if (aL5 !== bL5) return aL5 ? -1 : 1
    return toEpochMs(b.lastActiveAt) - toEpochMs(a.lastActiveAt)
  })
  return list
})

async function loadPatients() {
  patientsLoading.value = true
  patientsError.value = null
  try {
    const res = (await request.get('/api/doctor/patients')) as unknown
    const list = Array.isArray(res) ? (res as any[]) : ((res as any)?.patients as any[]) || []
    patients.value = (list || []).map((p) => ({
      userId: p.userId ?? p.id ?? p.patientId ?? p.user_id,
      latestEmotionLevel: p.latestEmotionLevel ?? p.latest_level ?? p.emotionLevel ?? p.latestEmotion ?? p.latest_emotion_level,
      lastActiveAt: p.lastActiveAt ?? p.lastActive ?? p.last_active_at ?? p.updatedAt ?? p.updated_at
    }))

    if (!selectedUserId.value && patients.value.length > 0) {
      const first = sortedPatients.value[0]!
      selectedUserId.value = String(first.userId)
      if (isMobile.value) mobilePane.value = 'detail'
      await loadPatientDetails(selectedUserId.value)
    }
  } catch (e: any) {
    patientsError.value = e?.message || 'Failed'
  } finally {
    patientsLoading.value = false
  }
}

function selectPatient(userId: string | number, forceMobileDetail = false) {
  selectedUserId.value = String(userId)
  if (isMobile.value && forceMobileDetail) mobilePane.value = 'detail'
}

// --- Details (3 endpoints) ---
const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const trend = ref<TrendPoint[]>([])
const summary = ref<PatientSummary | null>(null)
const crisisAlerts = ref<CrisisAlert[]>([])

let detailRequestSeq = 0

async function loadPatientDetails(userId: string | null) {
  if (!userId) return
  const seq = ++detailRequestSeq
  detailLoading.value = true
  detailError.value = null
  try {
    const [trendRes, summaryRes, crisisRes] = await Promise.all([
      request.get(`/api/doctor/patients/${encodeURIComponent(userId)}/emotion-trend`),
      request.get(`/api/doctor/patients/${encodeURIComponent(userId)}/summary`),
      request.get(`/api/doctor/patients/${encodeURIComponent(userId)}/crisis-alerts`)
    ])
    if (seq !== detailRequestSeq) return

    const trendList = Array.isArray(trendRes) ? (trendRes as any[]) : ((trendRes as any)?.trend as any[]) || []
    trend.value = trendList.map((t) => ({
      date: t.date ?? t.day ?? t.createdAt ?? t.created_at ?? '',
      level: t.level ?? t.emotionLevel ?? t.latestEmotionLevel ?? t.value ?? t.avgLevel ?? ''
    }))

    summary.value = (summaryRes as any) || null

    const crisisList = Array.isArray(crisisRes) ? (crisisRes as any[]) : ((crisisRes as any)?.alerts as any[]) || []
    crisisAlerts.value = (crisisList || []).map((a) => ({
      time: a.time ?? a.createdAt ?? a.created_at ?? a.timestamp ?? '',
      content: a.content ?? a.message ?? a.text ?? '',
      level: (a.level ?? a.emotionLevel ?? a.emotion_level ?? 'L3') as EmotionLevel
    }))
  } catch (e: any) {
    if (seq !== detailRequestSeq) return
    detailError.value = e?.message || 'Failed'
  } finally {
    if (seq !== detailRequestSeq) return
    detailLoading.value = false
    await nextTick()
    renderChart()
  }
}

watch(selectedUserId, async (id, prev) => {
  if (!id || id === prev) return
  await loadPatientDetails(id)
})

// --- Chart.js ---
const chartCanvas = ref<HTMLCanvasElement | null>(null)
let chart: Chart | null = null

function levelToNumber(level: EmotionLevel | string | number): number | null {
  if (typeof level === 'number') {
    if (Number.isFinite(level)) return level
    return null
  }
  const s = String(level).toUpperCase()
  if (s === 'L1') return 1
  if (s === 'L2') return 2
  if (s === 'L3') return 3
  if (s === 'L4') return 4
  if (s === 'L5') return 5
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}

function yTickLabel(v: number): string {
  const map: Record<number, string> = { 1: 'L1', 2: 'L2', 3: 'L3', 4: 'L4', 5: 'L5' }
  return map[v] ?? String(v)
}

function renderChart() {
  const canvas = chartCanvas.value
  if (!canvas) return

  const labels = trend.value.map((p, idx) => (p.date ? String(p.date).slice(5, 10) : `D${idx + 1}`))
  const data = trend.value.map((p) => levelToNumber(p.level))

  if (chart) {
    chart.destroy()
    chart = null
  }

  chart = new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: 'Emotion Level',
          data,
          borderColor: '#2563eb',
          backgroundColor: 'rgba(37, 99, 235, 0.12)',
          tension: 0.35,
          fill: true,
          pointRadius: 4,
          pointHoverRadius: 5,
          pointBackgroundColor: '#ffffff',
          pointBorderColor: '#2563eb',
          pointBorderWidth: 2
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${yTickLabel(Number(ctx.parsed.y))}`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(15, 23, 42, 0.06)' },
          ticks: { color: '#64748b', font: { size: 11 } }
        },
        y: {
          min: 1,
          max: 5,
          ticks: {
            stepSize: 1,
            color: '#64748b',
            callback: (v) => yTickLabel(Number(v))
          },
          grid: { color: 'rgba(15, 23, 42, 0.08)' }
        }
      }
    }
  })
}

onBeforeUnmount(() => {
  if (chart) chart.destroy()
  if (toastTimer) window.clearTimeout(toastTimer)
  window.removeEventListener('resize', updateIsMobile)
})

// --- FHIR report ---
const fhirLoading = ref(false)
async function generateFhirReport() {
  if (!selectedUserId.value) return
  fhirLoading.value = true
  try {
    await request.post('/mcp/tools/call', {
      jsonrpc: '2.0',
      id: '1',
      method: 'tools/call',
      params: {
        name: 'fhir_patient_summary',
        arguments: {
          fhirPatientId: '90254981',
          innerflowUserId: selectedUserId.value
        }
      }
    })
    showToast('success', 'Report Generated')
  } catch (e: any) {
    showToast('error', 'Failed to generate report', e?.message || 'Unknown error')
  } finally {
    fhirLoading.value = false
  }
}

// --- UI helpers ---
function levelPillClass(level?: EmotionLevel) {
  if (level === 'L1') return 'bg-emerald-50 text-emerald-700'
  if (level === 'L2') return 'bg-sky-50 text-sky-700'
  if (level === 'L3') return 'bg-amber-50 text-amber-800'
  if (level === 'L4') return 'bg-orange-50 text-orange-800'
  if (level === 'L5') return 'bg-rose-50 text-rose-700'
  return 'bg-slate-100 text-slate-700'
}

function formatRelativeTime(v?: string | number) {
  const ms = toEpochMs(v)
  if (!ms) return '—'
  const diff = Date.now() - ms
  const min = Math.floor(diff / 60000)
  if (min < 1) return 'Just now'
  if (min < 60) return `${min}m ago`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  return `${d}d ago`
}

function formatDateTime(v?: string) {
  if (!v) return '—'
  const ms = Date.parse(v)
  if (!Number.isFinite(ms)) return String(v)
  const d = new Date(ms)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(
    d.getHours()
  ).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(async () => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  if (isMobile.value) mobilePane.value = 'list'
  await loadPatients()
})
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>

