package org.base.model;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Measurement(name = "Book")
public class Book {
	@Column
	public Long viewCount;
	@Column
	public Long downloadCount;
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(tag = true)
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

}
