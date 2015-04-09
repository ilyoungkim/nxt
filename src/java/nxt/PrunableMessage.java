package nxt;

import nxt.crypto.EncryptedData;
import nxt.db.DbKey;
import nxt.db.DbUtils;
import nxt.db.PrunableDbTable;
import nxt.util.Convert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PrunableMessage {

    public static final DbKey.LongKeyFactory<PrunableMessage> prunableMessageKeyFactory = new DbKey.LongKeyFactory<PrunableMessage>("id") {

        @Override
        public DbKey newKey(PrunableMessage prunableMessage) {
            return prunableMessage.dbKey;
        }

    };

    public static final PrunableDbTable<PrunableMessage> prunableMessageTable = new PrunableDbTable<PrunableMessage>("prunable_message", prunableMessageKeyFactory) {

        @Override
        protected PrunableMessage load(Connection con, ResultSet rs) throws SQLException {
            return new PrunableMessage(rs);
        }

        @Override
        protected void save(Connection con, PrunableMessage prunableMessage) throws SQLException {
            prunableMessage.save(con);
        }

    };

    public static PrunableMessage getPrunableMessage(long transactionId) {
        return prunableMessageTable.get(prunableMessageKeyFactory.newKey(transactionId));
    }

    static void init() {}

    private final long id;
    private final DbKey dbKey;
    private final long senderId;
    private final long recipientId;
    private final byte[] message;
    private final EncryptedData encryptedData;
    private final boolean isText;
    private final int expiration;
    private final int blockTimestamp;

    private PrunableMessage(Transaction transaction, Appendix.PrunablePlainMessage appendix) {
        this.id = transaction.getId();
        this.dbKey = prunableMessageKeyFactory.newKey(this.id);
        this.senderId = transaction.getSenderId();
        this.recipientId = transaction.getRecipientId();
        this.message = appendix.getMessage();
        this.encryptedData = null;
        this.isText = appendix.isText();
        this.blockTimestamp = Nxt.getBlockchain().getLastBlockTimestamp();
        this.expiration = transaction.getTimestamp() + Constants.MIN_PRUNABLE_LIFETIME;
    }

    private PrunableMessage(Transaction transaction, Appendix.PrunableEncryptedMessage appendix) {
        this.id = transaction.getId();
        this.dbKey = prunableMessageKeyFactory.newKey(this.id);
        this.senderId = transaction.getSenderId();
        this.recipientId = transaction.getRecipientId();
        this.message = null;
        this.encryptedData = appendix.getEncryptedData();
        this.isText = appendix.isText();
        this.blockTimestamp = Nxt.getBlockchain().getLastBlockTimestamp();
        this.expiration = transaction.getTimestamp() + Constants.MIN_PRUNABLE_LIFETIME;
    }

    private PrunableMessage(ResultSet rs) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = prunableMessageKeyFactory.newKey(this.id);
        this.senderId = rs.getLong("sender_id");
        this.recipientId = rs.getLong("recipient_id");
        if (rs.getBoolean("is_encrypted")) {
            this.encryptedData = EncryptedData.readEncryptedData(rs.getBytes("message"));
            this.message = null;
        } else {
            this.message = rs.getBytes("message");
            this.encryptedData = null;
        }
        this.isText = rs.getBoolean("is_text");
        this.blockTimestamp = rs.getInt("timestamp");
        this.expiration = rs.getInt("expiration");
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO prunable_message (id, sender_id, recipient_id, "
                + "message, is_encrypted, is_text, timestamp, expiration, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.senderId);
            DbUtils.setLongZeroToNull(pstmt, ++i, this.recipientId);
            if (message != null) {
                pstmt.setBytes(++i, message);
                pstmt.setBoolean(++i, false);
            } else {
                pstmt.setBytes(++i, encryptedData.getBytes());
                pstmt.setBoolean(++i, true);
            }
            pstmt.setBoolean(++i, isText);
            pstmt.setInt(++i, blockTimestamp);
            pstmt.setInt(++i, expiration);
            pstmt.setInt(++i, Nxt.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public byte[] getMessage() {
        return message;
    }

    public EncryptedData getEncryptedData() {
        return encryptedData;
    }

    public boolean isText() {
        return isText;
    }

    @Override
    public String toString() {
        return isText ? Convert.toString(message) : Convert.toHexString(message);
    }

    static void add(Transaction transaction, Appendix.PrunablePlainMessage appendix) {
        if (Nxt.getEpochTime() - transaction.getTimestamp() < Constants.MIN_PRUNABLE_LIFETIME) {
            PrunableMessage prunableMessage = new PrunableMessage(transaction, appendix);
            prunableMessageTable.insert(prunableMessage);
        }
    }

    static void add(Transaction transaction, Appendix.PrunableEncryptedMessage appendix) {
        if (Nxt.getEpochTime() - transaction.getTimestamp() < Constants.MIN_PRUNABLE_LIFETIME) {
            PrunableMessage prunableMessage = new PrunableMessage(transaction, appendix);
            prunableMessageTable.insert(prunableMessage);
        }
    }
}
