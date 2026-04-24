package org.base.repository.impls;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.base.model.AuthorStats;
import org.base.model.Book;
import org.base.repository.BookRepository;
import org.base.util.JPAUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlBookRepository implements BookRepository {

	@Override
	public Page<Book> search(String title, String author, String content, Pageable pageable) {

		StringBuilder jpql = new StringBuilder("SELECT b FROM Book b WHERE 1=1");
		StringBuilder countJpql = new StringBuilder("SELECT COUNT(b) FROM Book b WHERE 1=1");

		try (EntityManager em = getEm()) {

			if (title != null && !title.trim().isEmpty()) {
				jpql.append(" AND LOWER(b.title) LIKE LOWER(:title)");
				countJpql.append(" AND LOWER(b.title) LIKE LOWER(:title)");
			}

			if (author != null && !author.trim().isEmpty()) {
				jpql.append(" AND LOWER(b.author) LIKE LOWER(:author)");
				countJpql.append(" AND LOWER(b.author) LIKE LOWER(:author)");
			}

			if (content != null && !content.trim().isEmpty()) {
				jpql.append(" AND LOWER(b.content) LIKE LOWER(:content)");
				countJpql.append(" AND LOWER(b.content) LIKE LOWER(:content)");
			}

			TypedQuery<Book> query = em.createQuery(jpql.toString(), Book.class);
			TypedQuery<Long> countQuery = em.createQuery(countJpql.toString(), Long.class);

			if (title != null && !title.trim().isEmpty()) {
				String value = "%" + title.trim() + "%";
				query.setParameter("title", value);
				countQuery.setParameter("title", value);
			}

			if (author != null && !author.trim().isEmpty()) {
				String value = "%" + author.trim() + "%";
				query.setParameter("author", value);
				countQuery.setParameter("author", value);
			}

			if (content != null && !content.trim().isEmpty()) {
				String value = "%" + content.trim() + "%";
				query.setParameter("content", value);
				countQuery.setParameter("content", value);
			}

			query.setFirstResult((int) pageable.getOffset());
			query.setMaxResults(pageable.getPageSize());

			List<Book> resultList = query.getResultList();
			long total = countQuery.getSingleResult();

			return new PageImpl<>(resultList, pageable, total);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void deleteByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) return;

		EntityManager em = getEm();
		EntityTransaction tx = em.getTransaction();

		try {
			List<Long> cleanIds = ids.stream()
					.filter(id -> id != null && !id.trim().isEmpty())
					.map(String::trim)
					.map(Long::parseLong)
					.toList();

			tx.begin();

			int batchSize = 500;
			for (int i = 0; i < cleanIds.size(); i += batchSize) {
				List<Long> batch = cleanIds.subList(i, Math.min(i + batchSize, cleanIds.size()));

				em.createQuery("DELETE FROM Book b WHERE b.id IN :ids")
						.setParameter("ids", batch)
						.executeUpdate();
			}

			tx.commit();
			System.out.println("Đã xóa thành công các ID trong SQL.");

		} catch (Exception e) {

			if (tx != null && tx.isActive()) {
				tx.rollback();
			}
			System.err.println("Lỗi khi xóa trong SQL: " + e.getMessage());
			throw new RuntimeException(e);
		} finally {
			if (em != null && em.isOpen()) {
				em.close();
			}
		}
	}


	@Override
	public void update(Long id, Book book) {
		EntityManager em = getEm();
		EntityTransaction tx = em.getTransaction();
		try {
			tx.begin();
			Book existingBook = em.find(Book.class, id);
			if (existingBook != null) {
				existingBook.setAuthor(getOrDefault(book.getAuthor(), existingBook.getAuthor()));
				existingBook.setCategory(getOrDefault(book.getCategory(), existingBook.getCategory()));
				existingBook.setTitle(getOrDefault(book.getTitle(), existingBook.getTitle()));
				existingBook.setContent(getOrDefault(book.getContent(), existingBook.getContent()));
			}
			tx.commit();
		} catch (Exception e) {
			if (tx.isActive()) tx.rollback();
			e.printStackTrace();
		}
		em.close();
	}

	@Override
	public Object findById(Long id) {
        EntityManager em = getEm();

        TypedQuery<Book> book = em.createQuery("SELECT b FROM Book b WHERE b.id = id", Book.class);
        em.close();

        return book;
	}


	@Override
	public void saveAll(List<Book> books) {
		EntityManager em = getEm();
		if (books == null || books.isEmpty()) return;
		EntityTransaction tx = em.getTransaction();
		try {
			tx.begin();
			for (int i = 0; i < books.size(); i++) {
				em.persist(books.get(i));

				if (i % 500 == 0 && i > 0) {
					em.flush();
					em.clear();
				}
			}
			tx.commit();
			em.clear();
		} catch (Exception e) {
			if (tx.isActive()) tx.rollback();
			e.printStackTrace();
		}
		em.close();
	}

	private EntityManager getEm() {
		return JPAUtil.getEntityManager();
	}

	@Override
	public Map<String, Object> statisticByAuthor(String author) {
		EntityManager em = getEm();

		String jpql = "SELECT v FROM AuthorStats v WHERE v.author = :author";
		List<AuthorStats> statsList = em.createQuery(jpql, AuthorStats.class)
				.setParameter("author", author)
				.getResultList();

		if (statsList.isEmpty()) {
			throw new EntityNotFoundException("Không tìm thấy: " + author);
		}

		long totalBooks = 0;
		Map<String, Long> categoryCountMap = new HashMap<>();

		for (AuthorStats s : statsList) {

			totalBooks += s.getBook_count();

			categoryCountMap.put(s.getCategory(), s.getBook_count());
		}

		Map<String, Object> result = new HashMap<>();
		result.put("author", author);
		result.put("total_books", totalBooks);
		result.put("categories", categoryCountMap);

		return result;
	}

	@Override
	public Page<Book> findAllPaging(Pageable pageable) {
		EntityManager em = getEm();

		List<Book> books = em.createQuery("SELECT b FROM Book b ORDER BY b.id DESC", Book.class)
				.setFirstResult((int) pageable.getOffset())
				.setMaxResults(pageable.getPageSize())
				.getResultList();

		Long total = em.createQuery("SELECT COUNT(b) FROM Book b", Long.class)
				.getSingleResult();

		em.close();

		return new PageImpl<>(books, pageable, total);
	}

	@Override
	public void save(Book book) {
		EntityManager em = getEm();
		EntityTransaction tx = em.getTransaction();
		try {
			tx.begin();
			em.persist(book);
			tx.commit();
		} catch (Exception e) {
			if (tx.isActive()) tx.rollback();
			e.printStackTrace();
			throw e;
		} finally {
			em.close();
		}
		System.out.println(book.getId() + " đã được lưu vào SQL.");
	}

	private String getOrDefault(String newVal, String oldVal) {
		return (newVal == null || newVal.trim().isEmpty()) ? oldVal : newVal;
	}
}