package org.base.model;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Measurement(name = "Book")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(tag = true)
    private Long id;

    private String author;
    private String category;
    private String title;
    private String content;
    private LocalDateTime createDate = LocalDateTime.now();
    private Long viewCount;
    private Long downloadCount;


}
