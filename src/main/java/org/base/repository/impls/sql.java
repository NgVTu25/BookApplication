package org.base.repository.impls;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import org.base.model.Book;
import org.base.repository.BookRepository;
import org.base.util.JPAUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class sql implements BookRepository {


    @Override
    public List<Book> search(String title, String author, String content) {
        StringBuilder jpql = new StringBuilder("SELECT b FROM Book b WHERE 1=1 ");

        if (author != null && !author.isEmpty()) jpql.append("AND LOWER(b.author) LIKE LOWER(:author) ");
        if (content != null && !content.isEmpty()) jpql.append("AND LOWER(b.content) LIKE LOWER(:content) ");

        EntityManager em = getEm();
        Query query = em.createQuery(jpql.toString(), Book.class);

        if (title != null && !title.isEmpty()) query.setParameter("name", "%" + title + "%");
        if (author != null && !author.isEmpty()) query.setParameter("author", "%" + author + "%");
        if (content != null && !content.isEmpty()) query.setParameter("content", "%" + content + "%");

        return query.getResultList();
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        EntityManager em = getEm();
        try {
            em.getTransaction().begin();
            String jpql = "DELETE FROM Book b WHERE b.id IN :ids";
            em.createQuery(jpql).setParameter("ids", ids).executeUpdate();
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
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
                existingBook.setAuthor(book.getAuthor());
                existingBook.setCategory(book.getCategory());
                existingBook.setTitle(book.getTitle());
                existingBook.setContent(book.getContent());
                existingBook.setViewCount(book.getViewCount());
                existingBook.setDownloadCount(book.getDownloadCount());

                em.merge(existingBook);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        }
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

        return result;
    }

    @Override
    public void save(Book book) {
        EntityManager em = getEm();
        em.getTransaction().begin();
        em.persist(book);
        em.getTransaction().commit();
    }
}