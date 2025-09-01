package org.example.syncwatchservice.repository;

import org.example.syncwatchservice.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    @Query("""
            SELECT m FROM Movie m 
            LEFT JOIN FETCH m.movieFiles mf 
            WHERE mf.uploadStatus = 'COMPLETED' 
            AND mf.fileType IN ('VIDEO', 'COVER')
            ORDER BY m.createdAt DESC
            """)
    List<Movie> findAllWithFiles();

    @Query("""
            SELECT m FROM Movie m 
            LEFT JOIN FETCH m.movieFiles mf 
            WHERE m.id = :id 
            AND mf.uploadStatus = 'COMPLETED'
            """)
    Optional<Movie> findByIdWithFiles(Long id);

    @Query("""
            SELECT DISTINCT m FROM Movie m 
            JOIN m.movieFiles mf 
            WHERE mf.fileType = 'VIDEO' 
            AND mf.uploadStatus = 'COMPLETED'
            ORDER BY m.title ASC
            """)
    List<Movie> findMoviesWithVideo();
}
