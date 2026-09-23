package com.EduePoa.EP.academics.service;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.Scoring.StudentsScore;
import com.EduePoa.EP.Scoring.StudentsScoreRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.entity.AssessmentFramework;
import com.EduePoa.EP.academics.assessment.entity.PerformanceLevel;
import com.EduePoa.EP.academics.assessment.enums.BroadPerformanceLevel;
import com.EduePoa.EP.academics.assessment.service.FrameworkResolutionService;
import com.EduePoa.EP.academics.assessment.service.PerformanceLevelService;
import com.EduePoa.EP.academics.assessment.service.impl.AssessmentCalculationServiceImpl;
import com.EduePoa.EP.academics.common.TermResolver;
import com.EduePoa.EP.academics.dto.response.CbcResultDto;
import com.EduePoa.EP.academics.entity.AcademicSubject;
import com.EduePoa.EP.academics.entity.CbcGradeResult;
import com.EduePoa.EP.academics.repository.CbcGradeResultRepository;
import com.EduePoa.EP.academics.service.impl.CbcCalculatorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-end style test of the results engine (section 80 scenario):
 * Grade 7 Mathematics, raw score 42 out of max 50 -> normalized 84% -> the applicable framework
 * resolves the performance level. The expected level is defined by framework configuration (data),
 * not hard-coded in the calculator.
 */
@ExtendWith(MockitoExtension.class)
class CbcCalculatorServiceImplTest {

    @Test
    @DisplayName("computeForGrade normalizes 42/50 to 84% and resolves the configured level")
    void computeForGrade_normalizesAndResolves() {
        CbcGradeResultRepository cbcRepo = mock(CbcGradeResultRepository.class);
        StudentsScoreRepository scoreRepo = mock(StudentsScoreRepository.class);
        GradeRepository gradeRepo = mock(GradeRepository.class);
        StudentRepository studentRepo = mock(StudentRepository.class);
        FrameworkResolutionService frameworkResolution = mock(FrameworkResolutionService.class);
        PerformanceLevelService performanceLevelService = mock(PerformanceLevelService.class);
        TermResolver termResolver = new TermResolver();
        AssessmentCalculationServiceImpl calc = new AssessmentCalculationServiceImpl();

        CbcCalculatorServiceImpl service = new CbcCalculatorServiceImpl(
                cbcRepo, scoreRepo, gradeRepo, studentRepo,
                termResolver, frameworkResolution, performanceLevelService, calc);

        // Fixtures
        Grade grade = new Grade();
        grade.setId(10L);
        grade.setName("Grade 7");
        when(gradeRepo.findById(10L)).thenReturn(Optional.of(grade));

        Student student = new Student();
        student.setId(100L);
        student.setFirstName("Test");
        student.setLastName("Learner");

        AcademicSubject maths = new AcademicSubject();
        maths.setId(5L);
        maths.setSubjectName("Mathematics");

        StudentsScore score = new StudentsScore();
        score.setStudent(student);
        score.setGrade(grade);
        score.setAcademicSubject(maths);
        score.setTerm(Term.TERM_1);
        score.setYear(Year.of(2027));
        score.setRawScore(new BigDecimal("42"));
        score.setMaximumScore(new BigDecimal("50"));

        when(scoreRepo.findByGradeAndTermAndYear(grade, Term.TERM_1, Year.of(2027)))
                .thenReturn(List.of(score));

        AssessmentFramework framework = new AssessmentFramework();
        framework.setId(1L);
        framework.setCode("KJSEA");
        when(frameworkResolution.resolveForGrade(grade)).thenReturn(Optional.of(framework));

        PerformanceLevel ee2 = new PerformanceLevel();
        ee2.setCode("EE2");
        ee2.setBroadLevel(BroadPerformanceLevel.EXCEEDING_EXPECTATION);
        ee2.setLabel("Exceeding Expectation 2");
        ee2.setAbbreviation("EE2");
        ee2.setMinScore(new BigDecimal("75"));
        ee2.setMaxScore(new BigDecimal("89"));
        ee2.setPoints(7);
        // 84% must fall into EE2 per configuration.
        when(performanceLevelService.resolve(eq(framework), any(BigDecimal.class)))
                .thenReturn(Optional.of(ee2));

        when(cbcRepo.findByStudentAndAcademicSubjectAndTermAndYear(student, maths, Term.TERM_1, Year.of(2027)))
                .thenReturn(Optional.empty());
        when(cbcRepo.save(any(CbcGradeResult.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomResponse<List<CbcResultDto>> response = service.computeForGrade(10L, 1L, 2027);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getEntity()).hasSize(1);
        CbcResultDto dto = response.getEntity().get(0);
        assertThat(dto.getNormalizedScore()).isEqualByComparingTo("84.00");
        assertThat(dto.getPerformanceLevelCode()).isEqualTo("EE2");
        assertThat(dto.getPerformancePoints()).isEqualTo(7);
        assertThat(dto.getCbcLevel()).isEqualTo(4); // EXCEEDING broad level

        // Verify the percentage passed to level resolution was exactly 84.00.
        verify(performanceLevelService).resolve(eq(framework), eq(new BigDecimal("84.00")));
    }
}
