package org.base.repository.impls;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
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

            em.close();
            return new PageImpl<>(resultList, pageable, total);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        EntityManager em = getEm();

        List<Long> cleanIds;
        try {
            cleanIds = ids.stream()
                    .filter(id -> id != null && !id.trim().isEmpty())
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Danh sách id chứa giá trị không hợp lệ", e);
        }

            for (Long id : cleanIds) {
                System.out.println(id);
            }

        try {
            em.getTransaction().begin();
            String jpql = "DELETE FROM Book b WHERE b.id IN :ids";
                System.out.println("Đã xóa id" + cleanIds);
            em.createQuery(jpql).setParameter("ids", cleanIds).executeUpdate();
            em.getTransaction().commit();
            em.close();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            e.printStackTrace();
            throw e;
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

                if (i % 1000 == 0 && i > 0) {
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
        Map<String, Object> result = new HashMap<>();
        EntityManager em = getEm();

        String countJpql = "SELECT COUNT(b) FROM Book b WHERE b.author = :author";
        Long totalBooks = (Long) em.createQuery(countJpql)
                .setParameter("author", author)
                .getSingleResult();
        result.put("Tổng số sách", totalBooks);

        String groupJpql = "SELECT b.category, COUNT(b) FROM Book b WHERE b.author = :author GROUP BY b.category";
        List<Object[]> categoryStats = em.createQuery(groupJpql, Object[].class)
                .setParameter("author", author)
                .getResultList();

        Map<String, Long> categoryCountMap = new HashMap<>();
        for (Object[] row : categoryStats) {
            String category = (String) row[0];
            Long count = (Long) row[1];
            categoryCountMap.put(category, count);
        }
        result.put("Thống kê theo thể loại", categoryCountMap);
        em.close();

        return result;
    }

    @Override
    public Page<Book> findAllPaging(Pageable pageable) {
        EntityManager em = getEm();

        List<Book> books = em.createQuery("SELECT b FROM Book b ORDER BY b.id DESC", Book.class)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        Long total = em.createQuery("SELECT COUNT(b) FROM Book b ORDER BY b.id DESC", Long.class)
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