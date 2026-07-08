package com.nagarjuna.mcpserver.repository;

import com.nagarjuna.mcpserver.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByCompletedFalse();

    List<Task> findByTitleContainingIgnoreCaseAndCompletedFalse(String title);

    List<Task> findByTitleContainingIgnoreCase(String title);

    List<Task> findByCompletedTrue();
}
