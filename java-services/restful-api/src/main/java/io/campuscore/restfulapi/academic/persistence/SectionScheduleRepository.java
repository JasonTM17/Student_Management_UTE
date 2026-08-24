package io.campuscore.restfulapi.academic.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionScheduleRepository extends JpaRepository<SectionScheduleEntity, String> {
    List<SectionScheduleEntity> findBySectionIdOrderByDayOfWeekAscStartTimeAsc(String sectionId);
}
