package team.projectpulse.student;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.activity.ActivityRepository;
import team.projectpulse.evaluation.PeerEvaluationRepository;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student: PATCH /students/{studentId}/deactivate.
 *
 * <p>Seed facts relied on: John (4) and Jerry (6) are on Team1 in course section 2 of course 1. Bingyang Wei is the
 * course admin of course 1 and, like Bill Gates (a plain instructor), is assigned to course sections 1 and 2. Tim
 * Cook is the course admin of course 3 and the instructor of its course section 3. Fixtures that the seed lacks (an
 * unassigned course admin, an instructor of another course section of the same course) are made in-test by
 * removing an instructor from course section 2; the base class rolls every change back.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;
    private static final int SECTION_2 = 2;
    private static final int NO_SUCH_STUDENT = 9999;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    ActivityRepository activityRepository;

    @Autowired
    PeerEvaluationRepository evaluationRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;
    String instructorBillToken;
    String adminTimToken;
    String studentJohnToken;
    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String username) throws Exception {
        String body = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(username, "123456")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getJSONObject("data").getString("token");
    }

    private ResultActions deactivate(int studentId, String token) throws Exception {
        return this.mockMvc.perform(patch(this.baseUrl + "/students/" + studentId + "/deactivate")
                .accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    private void unassignFromSection2(String instructorUsername) {
        Section section2 = this.sectionRepository.findById(SECTION_2).orElseThrow();
        Instructor instructor = section2.getInstructors().stream()
                .filter(i -> i.getUsername().equals(instructorUsername)).findFirst().orElseThrow();
        section2.removeInstructor(instructor);
        this.sectionRepository.saveAndFlush(section2);
    }

    private boolean johnIsEnabled() {
        return this.studentRepository.findById(JOHN).orElseThrow().isEnabled();
    }

    @Test
    void anAssignedInstructorDeactivatesAStudent() throws Exception {
        // Main success scenario
        Student before = this.studentRepository.findById(JOHN).orElseThrow();
        Integer teamId = before.getTeam().getTeamId();
        long activities = this.activityRepository.findAll().stream().filter(a -> a.getStudent().getId().equals(JOHN)).count();
        long evaluations = this.evaluationRepository.findAll().stream()
                .filter(e -> e.getEvaluator().getId().equals(JOHN) || e.getEvaluatee().getId().equals(JOHN)).count();

        deactivate(JOHN, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(JOHN))
                .andExpect(jsonPath("$.data.enabled").value(false));

        Student after = this.studentRepository.findById(JOHN).orElseThrow();
        assertThat(after.isEnabled()).isFalse();
        assertThat(after.getTeam().getTeamId()).isEqualTo(teamId); // She stays on her team
        assertThat(this.activityRepository.findAll().stream().filter(a -> a.getStudent().getId().equals(JOHN)).count()).isEqualTo(activities);
        assertThat(this.evaluationRepository.findAll().stream()
                .filter(e -> e.getEvaluator().getId().equals(JOHN) || e.getEvaluatee().getId().equals(JOHN)).count()).isEqualTo(evaluations);
        assertThat(activities).isPositive();
        assertThat(evaluations).isPositive();
    }

    @Test
    void theCourseAdminDeactivatesAStudentInACourseSectionSheIsNotAssignedTo() throws Exception {
        unassignFromSection2("b.wei@abc.edu");

        deactivate(JOHN, this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(johnIsEnabled()).isFalse();
    }

    @Test
    void anAlreadyDeactivatedStudentIsAConflict() throws Exception {
        // Extension 6a: deactivated by someone else after the caller viewed her details
        deactivate(JOHN, this.adminBingyangToken).andExpect(status().isOk());

        deactivate(JOHN, this.instructorBillToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));

        assertThat(johnIsEnabled()).isFalse();
    }

    @Test
    void anInstructorOfAnotherCourseSectionIsRefused() throws Exception {
        // Extension 6b: Bill stays an instructor of course section 1 of the same course, but not of John's
        unassignFromSection2("b.gates@abc.edu");

        deactivate(JOHN, this.instructorBillToken).andExpect(status().isForbidden());

        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void theAdminOfAnotherCourseIsRefused() throws Exception {
        // Extension 6b: Tim holds ROLE_instructor through the role hierarchy but administers course 3, not course 1
        deactivate(JOHN, this.adminTimToken).andExpect(status().isForbidden());

        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void theStudentHerselfIsRefused() throws Exception {
        deactivate(JOHN, this.studentJohnToken).andExpect(status().isForbidden());

        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void anotherStudentIsRefused() throws Exception {
        deactivate(JOHN, this.studentEricToken).andExpect(status().isForbidden());

        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void anIdNamingNoStudentIsForbiddenNotNotFound() throws Exception {
        deactivate(NO_SUCH_STUDENT, this.adminBingyangToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false));
    }

    @Test
    void aDeactivatedStudentsLiveTokenIsRefusedAfterDeactivation() throws Exception {
        // FR-SEC-active-account through the real endpoint: John's token was issued before the deactivation
        deactivate(JOHN, this.instructorBillToken).andExpect(status().isOk());

        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN)
                        .accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.studentJohnToken))
                .andExpect(status().isUnauthorized());
    }

}
