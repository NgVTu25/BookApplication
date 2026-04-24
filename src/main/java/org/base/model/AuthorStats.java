package org.base.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;


@Entity
@Data
@Table(name = "view_author_stats")
public class AuthorStats {
	@Id
	private String id;

	private String author;
	private String category;
	@Column(name = "book_count")
	private Long book_count;
}
