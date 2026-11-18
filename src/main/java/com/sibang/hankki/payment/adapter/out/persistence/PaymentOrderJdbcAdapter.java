package com.sibang.hankki.payment.adapter.out.persistence;

import com.sibang.hankki.payment.application.port.out.PaymentOrderPort;
import com.sibang.hankki.payment.domain.model.PaymentOrder;
import com.sibang.hankki.payment.domain.model.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentOrderJdbcAdapter implements PaymentOrderPort {

    private static final String COLUMNS = """
            id, reservation_id, merchant_reference, amount, currency, status,
            provider_transaction_id, response_code, expires_at, paid_at, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;

    public PaymentOrderJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PaymentOrder> findPendingByReservationId(UUID reservationId) {
        return jdbc.query("select " + COLUMNS + " from payment_orders "
                        + "where reservation_id = ? and status = 'PENDING' order by created_at desc limit 1",
                this::map, reservationId).stream().findFirst();
    }

    @Override
    public Optional<PaymentOrder> findByMerchantReferenceForUpdate(String merchantReference) {
        return jdbc.query("select " + COLUMNS + " from payment_orders where merchant_reference = ? for update",
                this::map, merchantReference).stream().findFirst();
    }

    @Override
    public PaymentOrder create(PaymentOrder order) {
        jdbc.update("""
                insert into payment_orders (
                    id, reservation_id, provider, merchant_reference, amount, currency, status,
                    expires_at, created_at, updated_at
                ) values (?, ?, 'VNPAY', ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), order.reservationId(), order.merchantReference(), order.amount(), order.currency(),
                order.status().name(), Timestamp.from(order.expiresAt()), Timestamp.from(order.createdAt()),
                Timestamp.from(order.updatedAt()));
        return order;
    }

    @Override
    public void complete(UUID id, PaymentStatus status, String providerTransactionId,
            String responseCode, Instant paidAt, Instant updatedAt) {
        jdbc.update("""
                update payment_orders
                set status = ?, provider_transaction_id = ?, response_code = ?, paid_at = ?, updated_at = ?
                where id = ?
                """, status.name(), providerTransactionId, responseCode,
                paidAt == null ? null : Timestamp.from(paidAt), Timestamp.from(updatedAt), id);
    }

    private PaymentOrder map(ResultSet rs, int rowNumber) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        return new PaymentOrder(
                rs.getObject("id", UUID.class), rs.getObject("reservation_id", UUID.class),
                rs.getString("merchant_reference"), rs.getLong("amount"), rs.getString("currency"),
                PaymentStatus.valueOf(rs.getString("status")), rs.getString("provider_transaction_id"),
                rs.getString("response_code"), rs.getTimestamp("expires_at").toInstant(),
                paidAt == null ? null : paidAt.toInstant(), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
