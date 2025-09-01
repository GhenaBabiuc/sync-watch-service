package org.example.syncwatchservice.repository;

import org.example.syncwatchservice.entity.MovieFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MovieFileRepository extends JpaRepository<MovieFile, Long> {

    @Query("""
            SELECT mf FROM MovieFile mf 
            WHERE mf.movie.id = :movieId 
            AND mf.fileType = :fileType 
            AND mf.uploadStatus = 'COMPLETED'
            """)
    Optional<MovieFile> findByMovieIdAndFileType(
            @Param("movieId") Long movieId,
            @Param("fileType") MovieFile.FileType fileType
    );

    @Query("""
            SELECT mf FROM MovieFile mf 
            WHERE mf.movie.id = :movieId 
            AND mf.fileType = 'VIDEO' 
            AND mf.uploadStatus = 'COMPLETED'
            """)
    Optional<MovieFile> findVideoByMovieId(@Param("movieId") Long movieId);

    @Query("""
            SELECT mf FROM MovieFile mf 
            WHERE mf.movie.id = :movieId 
            AND mf.fileType = 'COVER' 
            AND mf.uploadStatus = 'COMPLETED'
            """)
    Optional<MovieFile> findCoverByMovieId(@Param("movieId") Long movieId);
}
