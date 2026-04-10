package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @GetMapping
    public List<Course> getAllCourses() {
        return courseService.getAllCourses();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getCourseById(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @PostMapping
    public ResponseEntity<Course> createCourse(@RequestBody Course course) {
        try {
            Course savedCourse = courseService.createCourse(course);
            return ResponseEntity.ok(savedCourse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Course> updateCourse(@PathVariable UUID id, @RequestBody Course courseDetails) {
        try {
            Course updatedCourse = courseService.updateCourse(id, courseDetails);
            return ResponseEntity.ok(updatedCourse);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable UUID id) {
        try {
            courseService.deleteCourse(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
