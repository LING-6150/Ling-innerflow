<template>
  <div class="min-h-screen bg-slate-50 text-slate-900">

    <!-- Header -->
    <div class="sticky top-0 z-20 border-b border-slate-200 bg-white shadow-sm">
      <div class="mx-auto flex max-w-7xl items-center justify-between px-5 py-3">
        <div class="flex items-center gap-3">
          <div class="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-600 text-white text-sm font-bold shadow-sm">
            MD
          </div>
          <div>
            <div class="text-sm font-bold text-slate-900">InnerFlow Clinical Dashboard</div>
            <div class="text-xs text-slate-500">Mental health monitoring · AI-assisted</div>
          </div>
        </div>
        <div class="hidden items-center gap-4 text-xs text-slate-500 md:flex">
          <span><span class="font-semibold text-slate-800">{{ patients.length }}</span> patients</span>
          <span class="h-3 w-px bg-slate-200"></span>
          <span><span class="font-semibold text-rose-600">{{ highRiskCount }}</span> high-risk</span>
          <span class="h-3 w-px bg-slate-200"></span>
          <span>{{ todayDate }}</span>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <div class="pointer-events-none fixed right-4 top-16 z-50">
      <transition name="fade">
        <div v-if="toast.open"
          class="pointer-events-auto w-80 rounded-2xl border px-4 py-3 shadow-lg backdrop-blur"
          :class="toast.type === 'success'
            ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
            : 'border-rose-200 bg-rose-50 text-rose-900'">
          <div class="flex items-start gap-3">
            <div class="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[10px] font-bold"
              :class="toast.type === 'success' ? 'bg-emerald-600 text-white' : 'bg-rose-600 text-white'">
              {{ toast.type === 'success' ? '✓' : '!' }}
            </div>
            <div class="min-w-0">
              <div class="text-sm font-semibold">{{ toast.title }}</div>
              <div v-if="toast.message" class="mt-0.5 text-xs opacity-80">{{ toast.message }}</div>
            </div>
          </div>
        </div>
      </transition>
    </div>

    <div class="mx-auto max-w-7xl px-4 py-5">
      <div class="flex gap-5">

        <!-- ── Sidebar ── -->
        <aside class="hidden w-[264px] shrink-0 md:block">
          <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-4 py-3">
              <div class="text-sm font-semibold text-slate-900">Patients</div>
              <div class="mt-0.5 text-xs text-slate-500">Sorted by risk level</div>
              <!-- Search box -->
              <div class="relative mt-2">
                <svg class="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400"
                  fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                </svg>
                <input v-model="patientSearch" type="text" placeholder="Search patients…"
                  class="w-full rounded-lg border border-slate-200 bg-slate-50 py-1.5 pl-8 pr-3 text-xs text-slate-800 placeholder-slate-400 outline-none focus:border-blue-400 focus:bg-white" />
              </div>
            </div>
            <div class="max-h-[calc(100vh-180px)] overflow-y-auto">
              <!-- skeleton -->
              <div v-if="patientsLoading" class="space-y-2 p-3">
                <div v-for="i in 6" :key="i"
                  class="flex animate-pulse items-center gap-3 rounded-xl border border-slate-100 p-3">
                  <div class="h-9 w-9 shrink-0 rounded-full bg-slate-100"></div>
                  <div class="flex-1 space-y-1.5">
                    <div class="h-3.5 w-24 rounded bg-slate-100"></div>
                    <div class="h-3 w-16 rounded bg-slate-100"></div>
                  </div>
                </div>
              </div>
              <!-- error -->
              <div v-else-if="patientsError" class="p-4">
                <div class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
                  Failed to load.
                  <button class="mt-2 block rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700"
                    @click="loadPatients">Retry</button>
                </div>
              </div>
              <!-- empty search result -->
              <div v-else-if="filteredPatients.length === 0"
                class="p-4 text-center text-xs text-slate-400">
                No patients match "{{ patientSearch }}"
              </div>
              <!-- list -->
              <div v-else class="space-y-0.5 p-2">
                <button v-for="p in filteredPatients" :key="p.userId"
                  class="group w-full rounded-xl p-3 text-left transition-colors"
                  :class="selectedUserId === String(p.userId)
                    ? 'bg-blue-50 ring-1 ring-inset ring-blue-200'
                    : 'hover:bg-slate-50'"
                  @click="selectPatient(p.userId)">
                  <div class="flex items-center gap-2.5">
                    <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white"
                      :class="avatarBg(p.latestEmotionLevel)">
                      {{ String(p.userId).slice(0, 2).toUpperCase() }}
                    </div>
                    <div class="min-w-0 flex-1">
                      <div class="flex items-center justify-between gap-1">
                        <span class="truncate text-sm font-semibold text-slate-900">
                          Patient {{ p.userId }}
                        </span>
                        <span class="shrink-0 rounded-full px-2 py-0.5 text-xs font-semibold"
                          :class="levelPillClass(p.latestEmotionLevel)">
                          {{ p.latestEmotionLevel || '—' }}
                        </span>
                      </div>
                      <div class="mt-0.5 text-[11px] text-slate-500">
                        {{ formatRelativeTime(p.lastActiveAt) }}
                      </div>
                    </div>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </aside>

        <!-- ── Mobile list pane ── -->
        <div v-if="isMobile && mobilePane === 'list'" class="w-full md:hidden">
          <div class="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <div class="border-b border-slate-100 px-4 py-3">
              <div class="text-sm font-semibold">Patients</div>
              <div class="relative mt-2">
                <svg class="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400"
                  fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                </svg>
                <input v-model="patientSearch" type="text" placeholder="Search patients…"
                  class="w-full rounded-lg border border-slate-200 bg-slate-50 py-1.5 pl-8 pr-3 text-xs text-slate-800 placeholder-slate-400 outline-none focus:border-blue-400 focus:bg-white" />
              </div>
            </div>
            <div class="space-y-0.5 p-2">
              <button v-for="p in filteredPatients" :key="p.userId"
                class="w-full rounded-xl p-3 text-left transition hover:bg-slate-50"
                @click="selectPatient(p.userId, true)">
                <div class="flex items-center gap-2.5">
                  <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white"
                    :class="avatarBg(p.latestEmotionLevel)">
                    {{ String(p.userId).slice(0, 2).toUpperCase() }}
                  </div>
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between">
                      <span class="text-sm font-semibold">Patient {{ p.userId }}</span>
                      <span class="rounded-full px-2 py-0.5 text-xs font-semibold"
                        :class="levelPillClass(p.latestEmotionLevel)">
                        {{ p.latestEmotionLevel || '—' }}
                      </span>
                    </div>
                    <div class="mt-0.5 text-[11px] text-slate-500">{{ formatRelativeTime(p.lastActiveAt) }}</div>
                  </div>
                </div>
              </button>
            </div>
          </div>
        </div>

        <!-- ── Detail Panel ── -->
        <main v-if="!isMobile || mobilePane === 'detail'" class="min-w-0 flex-1 space-y-4">

          <!-- empty state -->
          <div v-if="!selectedUserId"
            class="flex h-64 items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white text-sm text-slate-400">
            Select a patient to view clinical details
          </div>

          <template v-else>
            <!-- Patient header -->
            <div class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
              <div class="flex items-center gap-3">
                <button v-if="isMobile"
                  class="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                  @click="mobilePane = 'list'">← Back</button>
                <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white"
                  :class="avatarBg(selectedPatient?.latestEmotionLevel)">
                  {{ String(selectedUserId).slice(0, 2).toUpperCase() }}
                </div>
                <div>
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="text-base font-bold text-slate-900">Patient {{ selectedUserId }}</span>
                    <span v-if="selectedPatient?.latestEmotionLevel"
                      class="rounded-full px-2.5 py-0.5 text-xs font-semibold"
                      :class="levelPillClass(selectedPatient.latestEmotionLevel)">
                      {{ selectedPatient.latestEmotionLevel }}
                    </span>
                    <span v-if="selectedPatient?.latestEmotionLevel === 'L5'"
                      class="flex items-center gap-1 rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-bold text-rose-700 ring-1 ring-rose-200">
                      ⚠ HIGH RISK
                    </span>
                  </div>
                  <div class="mt-0.5 text-xs text-slate-500">
                    Last active: {{ formatRelativeTime(selectedPatient?.lastActiveAt) }}
                  </div>
                </div>
              </div>
              <button
                class="flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                :disabled="!selectedUserId || fhirLoading"
                @click="generateFhirReport">
                <span v-if="fhirLoading"
                  class="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent"></span>
                <span>{{ fhirLoading ? 'Generating…' : 'Generate FHIR Report' }}</span>
              </button>
            </div>

            <!-- Detail loading skeleton -->
            <div v-if="detailLoading" class="space-y-4">
              <div class="animate-pulse rounded-2xl border border-slate-100 bg-white p-5">
                <div class="h-4 w-36 rounded bg-slate-100"></div>
                <div class="mt-4 h-[200px] rounded-xl bg-slate-100"></div>
              </div>
              <div class="grid grid-cols-1 gap-4 lg:grid-cols-5">
                <div v-for="i in 2" :key="i"
                  class="animate-pulse rounded-2xl border border-slate-100 bg-white p-5"
                  :class="i === 1 ? 'lg:col-span-2' : 'lg:col-span-3'">
                  <div class="h-4 w-28 rounded bg-slate-100"></div>
                  <div class="mt-4 space-y-2">
                    <div class="h-8 rounded bg-slate-100"></div>
                    <div class="h-16 rounded bg-slate-100"></div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Detail error -->
            <div v-else-if="detailError"
              class="rounded-2xl border border-rose-200 bg-rose-50 p-5 text-sm text-rose-800">
              Failed to load patient details.
              <button class="mt-3 inline-block rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700"
                @click="loadPatientDetails(selectedUserId)">Retry</button>
            </div>

            <template v-else>

              <!-- ① Trend Chart -->
              <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div class="flex items-center justify-between gap-3">
                  <div>
                    <div class="text-sm font-semibold text-slate-900">Emotion Trend</div>
                    <div class="mt-0.5 text-xs text-slate-500">Daily avg · L1 (minimal) → L5 (crisis)</div>
                  </div>
                  <div class="flex items-center gap-2">
                    <span v-if="trendLoading"
                      class="h-3.5 w-3.5 animate-spin rounded-full border-2 border-blue-400 border-t-transparent"></span>
                    <div class="flex items-center gap-1 rounded-xl bg-slate-100 p-1">
                      <button v-for="d in [7, 30, 90]" :key="d"
                        class="rounded-lg px-3 py-1 text-xs font-semibold transition"
                        :class="trendDays === d
                          ? 'bg-white text-blue-600 shadow-sm'
                          : 'text-slate-500 hover:text-slate-700'"
                        :disabled="trendLoading"
                        @click="setTrendDays(d)">
                        {{ d }}d
                      </button>
                    </div>
                  </div>
                </div>
                <div class="mt-4 h-[200px]">
                  <canvas ref="chartCanvas" class="h-full w-full"></canvas>
                </div>
              </div>

              <!-- ② PHQ-9 + L4 Reflection -->
              <div class="grid grid-cols-1 gap-4 lg:grid-cols-5">

                <!-- PHQ-9 -->
                <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm lg:col-span-2">
                  <div class="mb-3 text-sm font-semibold text-slate-900">PHQ-9 Screening</div>
                  <template v-if="summary?.phq9Severity">
                    <div class="flex flex-wrap items-baseline gap-2">
                      <span class="text-3xl font-bold tracking-tight text-slate-900">
                        {{ summary.phq9ScoreRange || '—' }}
                      </span>
                      <span class="rounded-full px-2.5 py-0.5 text-xs font-semibold"
                        :class="phq9SeverityClass(summary.phq9Severity)">
                        {{ summary.phq9Severity }}
                      </span>
                    </div>
                    <div v-if="summary.phq9KeyIndicators"
                      class="mt-3 rounded-xl border border-slate-100 bg-slate-50 p-3">
                      <div class="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                        Key Indicators
                      </div>
                      <ul class="space-y-1">
                        <li v-for="(line, i) in phq9Lines" :key="i"
                          class="flex gap-1.5 text-xs text-slate-700">
                          <span class="mt-0.5 shrink-0 text-slate-400">·</span>
                          <span>{{ line }}</span>
                        </li>
                      </ul>
                    </div>
                    <div v-if="summary.phq9Recommendation"
                      class="mt-2 rounded-xl border border-blue-100 bg-blue-50 p-3 text-xs text-blue-800 leading-relaxed">
                      {{ summary.phq9Recommendation }}
                    </div>
                  </template>
                  <div v-else class="text-sm italic text-slate-400">No data available.</div>
                  <div class="mt-3 text-[10px] text-slate-400">AI-assisted estimate · not a clinical diagnosis</div>
                </div>

                <!-- L4 Reflection -->
                <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm lg:col-span-3">
                  <div class="mb-3 flex items-center gap-2">
                    <div class="text-sm font-semibold text-slate-900">L4 Clinical Reflection</div>
                    <span class="rounded-full bg-purple-100 px-2 py-0.5 text-[10px] font-semibold text-purple-700">
                      AI-generated
                    </span>
                  </div>
                  <div v-if="summary?.l4Reflection"
                    class="rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm leading-relaxed text-slate-700">
                    {{ summary.l4Reflection }}
                  </div>
                  <div v-else
                    class="rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm italic text-slate-400">
                    No reflection yet. Generated automatically when a conversation session ends.
                  </div>
                </div>
              </div>

              <!-- ③ Patient Profile -->
              <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div class="mb-4 text-sm font-semibold text-slate-900">Patient Profile</div>
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <div class="rounded-xl border border-amber-100 bg-amber-50 p-4">
                    <div class="mb-2 flex items-center gap-2">
                      <div class="flex h-6 w-6 items-center justify-center rounded-lg bg-amber-200 text-amber-800 text-[11px] font-bold">≈</div>
                      <div class="text-[11px] font-semibold uppercase tracking-wide text-amber-800">Emotion Pattern</div>
                    </div>
                    <div class="text-sm text-slate-800">{{ summary?.emotionPattern || '—' }}</div>
                  </div>
                  <div class="rounded-xl border border-rose-100 bg-rose-50 p-4">
                    <div class="mb-2 flex items-center gap-2">
                      <div class="flex h-6 w-6 items-center justify-center rounded-lg bg-rose-200 text-rose-800 text-[11px] font-bold">!</div>
                      <div class="text-[11px] font-semibold uppercase tracking-wide text-rose-800">Core Struggles</div>
                    </div>
                    <div class="text-sm text-slate-800">{{ summary?.coreStruggles || '—' }}</div>
                  </div>
                  <div class="rounded-xl border border-emerald-100 bg-emerald-50 p-4">
                    <div class="mb-2 flex items-center gap-2">
                      <div class="flex h-6 w-6 items-center justify-center rounded-lg bg-emerald-200 text-emerald-800 text-[11px] font-bold">✓</div>
                      <div class="text-[11px] font-semibold uppercase tracking-wide text-emerald-800">Effective Coping</div>
                    </div>
                    <div class="text-sm text-slate-800">{{ summary?.effectiveCoping || '—' }}</div>
                  </div>
                </div>
              </div>

              <!-- ④ Crisis Alerts -->
              <div class="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div class="mb-4 flex items-center justify-between">
                  <div class="text-sm font-semibold text-slate-900">
                    Crisis Alerts
                    <span class="ml-1 font-normal text-slate-400">(last 30 days)</span>
                  </div>
                  <span class="rounded-full px-2.5 py-0.5 text-xs font-semibold"
                    :class="crisisAlerts.length > 0
                      ? 'bg-rose-100 text-rose-700'
                      : 'bg-slate-100 text-slate-600'">
                    {{ crisisAlerts.length }} record{{ crisisAlerts.length !== 1 ? 's' : '' }}
                  </span>
                </div>
                <div v-if="crisisAlerts.length === 0"
                  class="rounded-xl border border-slate-100 bg-slate-50 p-4 text-center text-sm text-slate-500">
                  No L5 crisis records in the past 30 days
                </div>
                <div v-else class="space-y-2">
                  <div v-for="(a, idx) in crisisAlerts" :key="idx"
                    class="rounded-xl border border-rose-200 bg-rose-50 p-4">
                    <div class="mb-2 flex items-center justify-between gap-2">
                      <span class="text-xs font-semibold text-rose-900">{{ formatDateTime(a.time) }}</span>
                      <span class="rounded-full bg-rose-600 px-2.5 py-0.5 text-xs font-bold text-white">
                        L5 Crisis
                      </span>
                    </div>
                    <div class="text-sm text-rose-900">{{ a.content }}</div>
                  </div>
                </div>
              </div>

              <!-- ⑤ FHIR Report panel (structured cards) -->
              <div v-if="parsedFhir"
                class="rounded-2xl border border-blue-200 bg-gradient-to-br from-blue-50 to-indigo-50 p-5 shadow-sm">
                <!-- Panel header -->
                <div class="mb-4 flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <div class="flex h-6 w-6 items-center justify-center rounded-lg bg-blue-600 text-[10px] font-bold text-white">R4</div>
                    <span class="text-sm font-semibold text-blue-900">FHIR Observation Report</span>
                    <span class="rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-semibold text-blue-700">HL7 FHIR R4</span>
                    <span class="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 uppercase">{{ parsedFhir.status }}</span>
                  </div>
                  <div class="flex items-center gap-3">
                    <span class="text-xs text-blue-600">Generated {{ fhirReportTime }}</span>
                    <button class="text-xs text-blue-500 underline hover:text-blue-700" @click="fhirReport = null">Dismiss</button>
                  </div>
                </div>

                <!-- Info row: Patient · Assessment · Time -->
                <div class="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <div class="rounded-xl border border-blue-100 bg-white px-4 py-3">
                    <div class="mb-1 text-[10px] font-semibold uppercase tracking-wide text-blue-500">Patient</div>
                    <div class="text-sm font-semibold text-slate-800">{{ parsedFhir.subject?.display ?? '—' }}</div>
                    <div class="mt-0.5 text-[11px] text-slate-400">{{ parsedFhir.subject?.reference }}</div>
                  </div>
                  <div class="rounded-xl border border-blue-100 bg-white px-4 py-3">
                    <div class="mb-1 text-[10px] font-semibold uppercase tracking-wide text-blue-500">Assessment</div>
                    <div class="text-sm font-semibold text-slate-800">{{ parsedFhir.code?.text ?? '—' }}</div>
                    <div class="mt-0.5 text-[11px] text-slate-400">LOINC {{ parsedFhir.code?.coding?.[0]?.code }}</div>
                  </div>
                  <div class="rounded-xl border border-blue-100 bg-white px-4 py-3">
                    <div class="mb-1 text-[10px] font-semibold uppercase tracking-wide text-blue-500">Effective Date</div>
                    <div class="text-sm font-semibold text-slate-800">{{ formatDateTime(parsedFhir.effectiveDateTime) }}</div>
                    <div class="mt-0.5 text-[11px] text-slate-400">Observation ID: {{ parsedFhir.id }}</div>
                  </div>
                </div>

                <!-- Clinical Note -->
                <div v-if="parsedFhir.note?.[0]?.text"
                  class="mb-3 rounded-xl border border-blue-100 bg-white px-4 py-3">
                  <div class="mb-1 text-[10px] font-semibold uppercase tracking-wide text-blue-500">Clinical Note</div>
                  <div class="text-xs text-slate-600 leading-relaxed">{{ parsedFhir.note[0].text }}</div>
                </div>

                <!-- Emotion Trend Report (main component) -->
                <div v-if="fhirTrendLines.length" class="rounded-xl border border-blue-100 bg-white px-4 py-3">
                  <div class="mb-2 text-[10px] font-semibold uppercase tracking-wide text-blue-500">
                    {{ parsedFhir.component?.[0]?.code?.text ?? 'Emotion Trend Analysis' }}
                  </div>
                  <div class="space-y-0.5">
                    <div v-for="(line, i) in fhirTrendLines" :key="i"
                      :class="[
                        'text-xs leading-relaxed',
                        line.type === 'heading' ? 'mt-2 font-semibold text-slate-700' :
                        line.type === 'bullet'  ? 'ml-2 text-slate-600' :
                        line.type === 'divider' ? 'hidden' :
                        'text-slate-500'
                      ]">
                      {{ line.text }}
                    </div>
                  </div>
                </div>
              </div>

            </template>
          </template>
        </main>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Chart from 'chart.js/auto'
import request from '@/api/request'

// ── Types ──────────────────────────────────────────────────────────

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
  // PHQ-9
  phq9ScoreRange?: string
  phq9Severity?: string
  phq9KeyIndicators?: string
  phq9Recommendation?: string
  phq9Screening?: string
  // Memory
  l4Reflection?: string
  emotionPattern?: string
  coreStruggles?: string
  effectiveCoping?: string
}

type CrisisAlert = {
  time: string
  content: string
  level: EmotionLevel
}

// ── Mobile ─────────────────────────────────────────────────────────

const isMobile = ref(false)
const mobilePane = ref<'list' | 'detail'>('list')

function updateIsMobile() {
  isMobile.value = window.matchMedia('(max-width: 767px)').matches
}

// ── Toast ──────────────────────────────────────────────────────────

const toast = ref<{ open: boolean; type: 'success' | 'error'; title: string; message?: string }>({
  open: false, type: 'success', title: ''
})
let toastTimer: number | undefined

function showToast(type: 'success' | 'error', title: string, message?: string) {
  toast.value = { open: true, type, title, message }
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value.open = false }, 3500)
}

// ── Header stats ───────────────────────────────────────────────────

const todayDate = new Date().toLocaleDateString('en-US', {
  year: 'numeric', month: 'short', day: 'numeric'
})

const highRiskCount = computed(() =>
  patients.value.filter(p => p.latestEmotionLevel === 'L5' || p.latestEmotionLevel === 'L4').length
)

// ── Patients list ──────────────────────────────────────────────────

const patients = ref<PatientItem[]>([])
const patientsLoading = ref(false)
const patientsError = ref<string | null>(null)
const selectedUserId = ref<string | null>(null)
const patientSearch = ref('')

const selectedPatient = computed(() =>
  patients.value.find(p => String(p.userId) === String(selectedUserId.value)) ?? null
)

const filteredPatients = computed(() => {
  const q = patientSearch.value.trim().toLowerCase()
  if (!q) return sortedPatients.value
  return sortedPatients.value.filter(p =>
    String(p.userId).toLowerCase().includes(q)
  )
})

function toEpochMs(v?: string | number): number {
  if (v == null) return 0
  if (typeof v === 'number') return v > 10_000_000_000 ? v : v * 1000
  const t = Date.parse(v)
  return Number.isFinite(t) ? t : 0
}

const sortedPatients = computed(() => {
  const levelOrder: Record<string, number> = { L5: 0, L4: 1, L3: 2, L2: 3, L1: 4 }
  return [...patients.value].sort((a, b) => {
    const la = levelOrder[a.latestEmotionLevel ?? ''] ?? 5
    const lb = levelOrder[b.latestEmotionLevel ?? ''] ?? 5
    if (la !== lb) return la - lb
    return toEpochMs(b.lastActiveAt) - toEpochMs(a.lastActiveAt)
  })
})

async function loadPatients() {
  patientsLoading.value = true
  patientsError.value = null
  try {
    const res = (await request.get('/api/doctor/patients')) as unknown
    const list = Array.isArray(res) ? (res as any[]) : ((res as any)?.patients ?? [])
    patients.value = list.map((p: any) => ({
      userId: p.userId ?? p.id ?? p.patientId ?? p.user_id,
      // Backend now returns "L{n}" string directly
      latestEmotionLevel: p.latestEmotionLevel ?? p.latest_level ?? undefined,
      lastActiveAt: p.lastActiveAt ?? p.lastActive ?? p.last_active_at ?? p.updatedAt
    }))
    if (!selectedUserId.value && patients.value.length > 0) {
      const first = sortedPatients.value[0]!
      selectedUserId.value = String(first.userId)
      if (!isMobile.value) await loadPatientDetails(selectedUserId.value)
    }
  } catch (e: any) {
    patientsError.value = e?.message ?? 'Failed'
  } finally {
    patientsLoading.value = false
  }
}

function selectPatient(userId: string | number, mobile = false) {
  selectedUserId.value = String(userId)
  if (mobile) mobilePane.value = 'detail'
  if (!isMobile.value || mobile) loadPatientDetails(String(userId))
}

// ── Detail data ────────────────────────────────────────────────────

const detailLoading = ref(false)
const detailError = ref<string | null>(null)
const trend = ref<TrendPoint[]>([])
const summary = ref<PatientSummary | null>(null)
const crisisAlerts = ref<CrisisAlert[]>([])
let detailSeq = 0

async function loadPatientDetails(userId: string | null) {
  if (!userId) return
  const seq = ++detailSeq
  detailLoading.value = true
  detailError.value = null
  try {
    const [trendRes, summaryRes, crisisRes] = await Promise.all([
      request.get(`/api/doctor/patients/${encodeURIComponent(userId)}/emotion-trend?days=${trendDays.value}`),
      request.get(`/api/doctor/patients/${encodeURIComponent(userId)}/summary`),
      request.get(`/api/doctor/patients/${encodeURIComponent(userId)}/crisis-alerts`)
    ])
    if (seq !== detailSeq) return

    const trendList = Array.isArray(trendRes) ? (trendRes as any[]) : ((trendRes as any)?.trend ?? [])
    trend.value = trendList.map((t: any) => ({
      date: t.date ?? t.day ?? '',
      level: t.level ?? t.emotionLevel ?? ''
    }))

    summary.value = (summaryRes as any) ?? null

    const crisisList = Array.isArray(crisisRes) ? (crisisRes as any[]) : ((crisisRes as any)?.alerts ?? [])
    crisisAlerts.value = crisisList.map((a: any) => ({
      time: a.time ?? a.createdAt ?? a.created_at ?? a.timestamp ?? '',
      content: a.content ?? a.message ?? a.text ?? '',
      level: (a.level ?? a.emotionLevel ?? a.emotion_level ?? 'L5') as EmotionLevel
    }))
  } catch (e: any) {
    if (seq !== detailSeq) return
    detailError.value = e?.message ?? 'Failed'
  } finally {
    if (seq !== detailSeq) return
    detailLoading.value = false
    await nextTick()
    renderChart()
  }
}

watch(selectedUserId, (id, prev) => {
  if (id && id !== prev) loadPatientDetails(id)
})

// ── Trend days selector ────────────────────────────────────────────

const trendDays = ref(7)
const trendLoading = ref(false)

async function loadTrendOnly() {
  const userId = selectedUserId.value
  if (!userId) return
  trendLoading.value = true
  try {
    const res = await request.get(
      `/api/doctor/patients/${encodeURIComponent(userId)}/emotion-trend?days=${trendDays.value}`
    )
    const list = Array.isArray(res) ? (res as any[]) : ((res as any)?.trend ?? [])
    trend.value = list.map((t: any) => ({
      date: t.date ?? t.day ?? '',
      level: t.level ?? t.emotionLevel ?? ''
    }))
    await nextTick()
    renderChart()
  } catch (e) {
    console.error('Trend reload failed', e)
  } finally {
    trendLoading.value = false
  }
}

async function setTrendDays(d: number) {
  if (trendDays.value === d) return
  trendDays.value = d
  await loadTrendOnly()
}

// ── Chart ──────────────────────────────────────────────────────────

const chartCanvas = ref<HTMLCanvasElement | null>(null)
let chart: Chart | null = null

function levelToNum(level: EmotionLevel | string | number): number | null {
  if (typeof level === 'number') return Number.isFinite(level) ? level : null
  const s = String(level).toUpperCase().trim()
  if (s === 'L1') return 1
  if (s === 'L2') return 2
  if (s === 'L3') return 3
  if (s === 'L4') return 4
  if (s === 'L5') return 5
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}

const yLabel = (v: number) => ({ 1: 'L1', 2: 'L2', 3: 'L3', 4: 'L4', 5: 'L5' }[v] ?? String(v))

const pointColors = (data: (number | null)[]) =>
  data.map(v => {
    if (v === null) return 'rgba(0,0,0,0)'
    if (v >= 5) return '#ef4444'
    if (v >= 4) return '#f97316'
    if (v >= 3) return '#f59e0b'
    if (v >= 2) return '#3b82f6'
    return '#10b981'
  })

function renderChart() {
  const canvas = chartCanvas.value
  if (!canvas) return
  const labels = trend.value.map((p, i) => p.date ? String(p.date).slice(5, 10) : `D${i + 1}`)
  const data = trend.value.map(p => levelToNum(p.level))
  const colors = pointColors(data)

  chart?.destroy()
  chart = new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Emotion Level',
        data,
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59,130,246,0.08)',
        tension: 0.3,
        fill: true,
        pointRadius: 5,
        pointHoverRadius: 7,
        pointBackgroundColor: colors,
        pointBorderColor: colors,
        pointBorderWidth: 0,
        spanGaps: false
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: ctx => ` ${yLabel(Number(ctx.parsed.y))}`
          }
        }
      },
      scales: {
        x: {
          grid: { color: 'rgba(15,23,42,0.05)' },
          ticks: { color: '#94a3b8', font: { size: 11 } }
        },
        y: {
          min: 1, max: 5,
          ticks: { stepSize: 1, color: '#94a3b8', callback: v => yLabel(Number(v)) },
          grid: { color: 'rgba(15,23,42,0.06)' }
        }
      }
    }
  })
}

// ── FHIR Report ────────────────────────────────────────────────────

const fhirLoading = ref(false)
const fhirReport = ref<string | null>(null)
const fhirReportTime = ref('')

async function generateFhirReport() {
  if (!selectedUserId.value) return
  fhirLoading.value = true
  try {
    const res = await request.post('/mcp/tools/call', {
      jsonrpc: '2.0', id: '1', method: 'tools/call',
      params: {
        name: 'fhir_patient_summary',
        arguments: { fhirPatientId: '90254981', innerflowUserId: selectedUserId.value }
      }
    }, { timeout: 60000 }) as any

    // Extract text content from MCP response
    const text: string | null =
      res?.result?.content?.[0]?.text ??
      res?.result?.content ??
      null

    if (text) {
      fhirReport.value = text
      fhirReportTime.value = new Date().toLocaleTimeString()
      showToast('success', 'FHIR Report Generated', 'Scroll down to view the report')
    } else {
      showToast('success', 'Report Generated')
    }
  } catch (e: any) {
    showToast('error', 'Failed to generate report', e?.message ?? 'Unknown error')
  } finally {
    fhirLoading.value = false
  }
}

// ── FHIR parsed view ───────────────────────────────────────────────

const parsedFhir = computed<Record<string, any> | null>(() => {
  if (!fhirReport.value) return null
  try {
    const jsonStart = fhirReport.value.indexOf('{')
    if (jsonStart === -1) return null
    return JSON.parse(fhirReport.value.slice(jsonStart))
  } catch {
    return null
  }
})

type FhirLine = { type: 'heading' | 'bullet' | 'divider' | 'text'; text: string }

const fhirTrendLines = computed<FhirLine[]>(() => {
  const raw: string = parsedFhir.value?.component?.[0]?.valueString ?? ''
  if (!raw) return []
  return raw.split('\n').map((line): FhirLine => {
    const t = line.trim()
    if (!t || t.startsWith('===')) return { type: 'divider', text: t }
    if (/^[A-Z][A-Z ]+$/.test(t)) return { type: 'heading', text: t }
    if (t.startsWith('•') || t.startsWith('-')) return { type: 'bullet', text: t }
    return { type: 'text', text: t }
  }).filter(l => l.type !== 'divider' || false)
})

// ── PHQ-9 helpers ──────────────────────────────────────────────────

const phq9Lines = computed(() =>
  (summary.value?.phq9KeyIndicators ?? '')
    .split('\n')
    .map(l => l.trim())
    .filter(Boolean)
)

function phq9SeverityClass(s?: string): string {
  if (!s) return 'bg-slate-100 text-slate-700'
  const lower = s.toLowerCase()
  if (lower.includes('severe') && lower.includes('moderate')) return 'bg-orange-100 text-orange-800'
  if (lower.includes('severe')) return 'bg-rose-100 text-rose-800'
  if (lower.includes('moderate')) return 'bg-amber-100 text-amber-800'
  if (lower.includes('mild')) return 'bg-yellow-100 text-yellow-800'
  return 'bg-emerald-100 text-emerald-800'
}

// ── UI helpers ─────────────────────────────────────────────────────

function levelPillClass(level?: EmotionLevel | string): string {
  if (level === 'L1') return 'bg-emerald-100 text-emerald-700'
  if (level === 'L2') return 'bg-sky-100 text-sky-700'
  if (level === 'L3') return 'bg-amber-100 text-amber-800'
  if (level === 'L4') return 'bg-orange-100 text-orange-800'
  if (level === 'L5') return 'bg-rose-100 text-rose-700'
  return 'bg-slate-100 text-slate-600'
}

function avatarBg(level?: EmotionLevel | string): string {
  if (level === 'L5') return 'bg-rose-500'
  if (level === 'L4') return 'bg-orange-500'
  if (level === 'L3') return 'bg-amber-500'
  if (level === 'L2') return 'bg-sky-500'
  if (level === 'L1') return 'bg-emerald-500'
  return 'bg-slate-400'
}

function formatRelativeTime(v?: string | number): string {
  const ms = toEpochMs(v)
  if (!ms) return '—'
  const diff = Date.now() - ms
  const min = Math.floor(diff / 60000)
  if (min < 1) return 'Just now'
  if (min < 60) return `${min}m ago`
  const h = Math.floor(min / 60)
  if (h < 24) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

function formatDateTime(v?: string): string {
  if (!v) return '—'
  const ms = Date.parse(v)
  if (!Number.isFinite(ms)) return String(v)
  const d = new Date(ms)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ` +
    `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// ── Lifecycle ──────────────────────────────────────────────────────

onMounted(async () => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  await loadPatients()
})

onBeforeUnmount(() => {
  chart?.destroy()
  if (toastTimer) window.clearTimeout(toastTimer)
  window.removeEventListener('resize', updateIsMobile)
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
