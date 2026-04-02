package org.base.model;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Measurement(name = "Book")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(tag = true)
    private String author;
    @Column(tag = true)
    private String category;
    @Column
    private String title;
    @Column
    private String content;
    @Column(timestamp = true)
    private Instant createDate = Instant.now();
    @Column
    public Long viewCount;
    @Column
    public Long downloadCount;

}
