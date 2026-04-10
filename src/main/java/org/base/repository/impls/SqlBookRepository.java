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
        Map<String, String> params = new HashMap<>();

        if (title != null && !title.trim().isEmpty()) params.put("title", "%" + title + "%");
        if (author != null && !author.trim().isEmpty()) params.put("author", "%" + author + "%");
        if (content != null && !content.trim().isEmpty()) params.put("content", "%" + content + "%");

        params.keySet().forEach(k -> jpql.append(" AND LOWER(b.").append(k).append(") LIKE LOWER(:").append(k).append(")"));

        try (EntityManager em = getEm()) {
            TypedQuery<Book> query = em.createQuery(jpql.toString(), Book.class);

            params.forEach(query::setParameter);

            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
            List<Book> resultList = query.getResultList();

            String countJpql = jpql.toString().replace("SELECT b", "SELECT COUNT(b)");
            TypedQuery<Long> countQuery = em.createQuery(countJpql, Long.class);
            params.forEach(countQuery::setParameter);
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
    }

    private String getOrDefault(String newVal, String oldVal) {
        return (newVal == null || newVal.trim().isEmpty()) ? oldVal : newVal;
    }
}