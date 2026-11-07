<template>
  <el-card class="page-container">
    <template #header>
      <div class="header">
        <span
          >Submit Peer Evaluations for week:
          <el-tag type="primary" style="font-size: 14px; font-weight: bold">{{
            previousWeekRange
          }}</el-tag></span
        >
      </div>
      <div style="margin-top: 10px">
        <span>
          The default score for each criterion is 10. You can change it to a value between 0 and 10.
        </span>
        <!-- Remind the user that "You have NOT submitted the evaluation" -->
        <el-tag v-if="isNewSubmission" type="danger" style="font-size: 14px; font-weight: bold"
          >You have NOT submitted the evaluation. Click the "Submit" button.</el-tag
        >
      </div>
    </template>
    <!-- Evaluation Table, adding height can fix the table header -->
    <el-table
      :data="peerEvalutions"
      style="width: 100%"
      stripe
      border
      height="600"
      v-loading="loading"
      scrollbar-always-on
    >
      <el-table-column
        label="Student Name"
        prop="evaluateeName"
        min-width="100"
        fixed
      ></el-table-column>
      <el-table-column v-for="criterion in criteria" :key="criterion.criterionId" min-width="150">
        <template #header>
          <el-tooltip effect="dark" :content="criterion.criterion" placement="top">
            <span>{{ criterion.description }}</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <div style="display: flex; justify-content: center; align-items: center">
            <el-input-number
              v-model="
                row.ratings.find((r: Rating) => r.criterionId === criterion.criterionId).actualScore
              "
              :min="0"
              :max="criterion.maxScore"
              :step="1"
              controls-position="right"
              style="width: 80px"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column min-width="200px">
        <template #header>
          <el-tooltip
            effect="dark"
            content="Public comments are available to the evaluatee."
            placement="top"
          >
            <span>Public Comment</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <el-input
            v-model="row.publicComment"
            type="textarea"
            :rows="5"
            placeholder="Enter public comment"
            max-length="255"
            show-word-limit
          />
        </template>
      </el-table-column>
      <el-table-column min-width="200px">
        <template #header>
          <el-tooltip
            effect="dark"
            content="Private comments are ONLY available to the instructor."
            placement="top"
          >
            <span>Private Comment</span>
          </el-tooltip>
        </template>
        <template #default="{ row }">
          <el-input
            v-model="row.privateComment"
            type="textarea"
            :rows="5"
            placeholder="Enter private comment"
            max-length="255"
            show-word-limit
          />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No data is available." />
      </template>
    </el-table>
    <!-- submit button on the right side-->
    <div style="display: flex; justify-content: flex-end">
      <el-button
        type="primary"
        @click="submitPeerEvaluations"
        v-if="isNewSubmission"
        :loading="isLoading"
        >Submit</el-button
      >
      <el-button type="warning" @click="updatePeerEvaluations" :loading="isLoading" v-else
        >Update</el-button
      >
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { findStudentsByTeamId } from '@/apis/student'
import type { Student, FindStudentByTeamIdResponse } from '@/apis/student/types'
import { findSectionById } from '@/apis/section'
import { findRubricById } from '@/apis/rubric'
import type { FindRubricByIdResponse, Criterion } from '@/apis/rubric/types'
import { getPreviousWeek, getPreviousWeekRange } from '@/utils/week'
import { useUserInfoStore } from '@/stores/userInfo'
import type { FindSectionByIdResponse } from '@/apis/section/types'
import {
  submitEvaluation,
  getEvaluationsByEvaluatorIdAndWeek,
  updateEvaluation,
  sendPeerEvaluationSubmissionConfirmationEmail
} from '@/apis/evaluation'
import { ElMessage } from 'element-plus'
import type { FetchPeerEvaluationsResponse, PeerEvaluation, Rating } from '@/apis/evaluation/types'

const previousWeek = ref<string>('')
const previousWeekRange = ref<string>('')

const peerEvalutions = ref<PeerEvaluation[]>()

const loading = ref<boolean>(false)

let isNewSubmission = true // Flag to indicate if this is a new submission or an update

const teamId = ref<number>(NaN) // Team ID of the student

const students = ref<Student[]>([]) // Students on the same team
const sectionId = ref<number>() // Section ID of the team
const criteria = ref<Criterion[]>([]) // Criteria used for peer evaluations
const isLoading = ref(false)

const userInfoStore = useUserInfoStore() // Need team ID to search for students

// Load data when the component is mounted
onMounted(async () => {
  previousWeek.value = getPreviousWeek()
  previousWeekRange.value = getPreviousWeekRange()
  teamId.value = (userInfoStore.userInfo as Student).teamId as number
  if (!teamId.value) {
    ElMessage.error('You have not been assigned a team yet.')
    return
  }
  await loadStudents()
  sectionId.value = (userInfoStore.userInfo as Student).sectionId
  await loadCriteria()
  loading.value = true
  prepareStartingPeerEvaluations()
  loading.value = false
})

// Load the teammates to evaluate. A deactivated teammate is not asked for (UC-EVA-submit-evaluation step 2,
// BR-student-lifecycle); the server refuses a rating of her anyway (9a).
async function loadStudents() {
  const result: FindStudentByTeamIdResponse = await findStudentsByTeamId(teamId.value)
  students.value = result.data.filter((student) => student.enabled !== false)
}

// Load criteria information
async function loadCriteria() {
  const sectionResult: FindSectionByIdResponse = await findSectionById(sectionId.value as number)
  const rubricResult: FindRubricByIdResponse = await findRubricById(
    sectionResult.data.rubricId as number
  )
  criteria.value = rubricResult.data.criteria!
}

// Create starting peer evaluations
async function prepareStartingPeerEvaluations() {
  // Get the evaluations for the current week
  const result: FetchPeerEvaluationsResponse = await getEvaluationsByEvaluatorIdAndWeek(
    (userInfoStore.userInfo as Student).id as number,
    previousWeek.value
  )

  // If the student has already submitted evaluations for the current week, load them, leaving out any of a
  // teammate deactivated since: an edit of that rating would be refused (9a)
  if (result.data.length > 0) {
    const activeIds = new Set(students.value.map((student) => student.id))
    peerEvalutions.value = result.data.filter((evaluation) => activeIds.has(evaluation.evaluateeId))
    isNewSubmission = false
  } else {
    // If not, create a new set of evaluations
    isNewSubmission = true
    peerEvalutions.value = students.value.map((student) => ({
      week: previousWeek.value,
      evaluatorId: (userInfoStore.userInfo as Student).id as number,
      evaluateeId: student.id as number,
      evaluateeName: `${student.firstName} ${student.lastName}`,
      ratings: criteria.value.map((criterion) => ({
        criterionId: criterion.criterionId as number,
        criterion: criterion.criterion,
        actualScore: 10 // Default score, can be changed by the user
      })),
      publicComment: '',
      privateComment: ''
    }))
  }
}

// Each rating is its own request and the server judges each alone, so one refused rating (9a: a teammate
// deactivated while this page was open) must not hide the ones that saved. When any is refused, the shared
// Axios instance has already shown why; reload the form from the server so it shows what was stored and no
// longer lists her.
async function saveAll(
  save: (evaluation: PeerEvaluation) => Promise<{ data: PeerEvaluation }>
): Promise<boolean> {
  const results = await Promise.allSettled(peerEvalutions.value!.map(save))
  if (results.every((r) => r.status === 'fulfilled')) {
    peerEvalutions.value = results.map(
      (r) => (r as PromiseFulfilledResult<{ data: PeerEvaluation }>).value.data
    )
    return true
  }
  await loadStudents()
  await prepareStartingPeerEvaluations()
  return false
}

// Submit peer evaluations
async function submitPeerEvaluations() {
  if (peerEvalutions.value) {
    isLoading.value = true
    let allSaved: boolean
    try {
      allSaved = await saveAll(submitEvaluation)
    } finally {
      isLoading.value = false
    }
    if (!allSaved) {
      ElMessage.warning('Some peer evaluations were not saved. The form shows what was saved.')
      return
    }
    isNewSubmission = false // Change to update mode
    ElMessage.success('Peer evaluations submitted successfully')

    // Send an submission confirmation email to the submitter
    await sendPeerEvaluationSubmissionConfirmationEmail(previousWeekRange.value)
    ElMessage.success('A confirmation email has been sent to you.')
  }
}

// Update peer evaluations
async function updatePeerEvaluations() {
  if (peerEvalutions.value) {
    isLoading.value = true
    let allSaved: boolean
    try {
      allSaved = await saveAll(updateEvaluation)
    } finally {
      isLoading.value = false
    }
    if (allSaved) {
      ElMessage.success('Peer evaluations updated successfully')
    } else {
      ElMessage.warning('Some peer evaluations were not saved. The form shows what was saved.')
    }
  }
}
</script>

<style lang="scss" scoped>
.page-container {
  min-height: 100%;
  box-sizing: border-box;
  .header {
    display: flex;
    align-items: center;
    gap: 20px;
  }
}
</style>
