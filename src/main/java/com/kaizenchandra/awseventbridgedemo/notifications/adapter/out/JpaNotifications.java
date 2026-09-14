package com.kaizenchandra.awseventbridgedemo.notifications.adapter.out;
import org.springframework.stereotype.Repository;import java.util.*;
import com.kaizenchandra.awseventbridgedemo.notifications.application.NotificationPort;import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
@Repository public class JpaNotifications implements NotificationPort {
 private final Sql sql;public JpaNotifications(Sql sql){this.sql=sql;}
 public List<Map<String,Object>> inbox(String owner,int page,int size){return sql.rows("SELECT n.booking_id,n.status,n.version,n.updated_at FROM notification n JOIN booking b ON b.id=n.booking_id WHERE b.owner=?1 ORDER BY n.updated_at DESC,n.booking_id LIMIT ?2 OFFSET ?3",owner,size,page*size).stream().map(r->Map.of("bookingId",r[0],"status",r[1],"version",r[2],"updatedAt",r[3])).toList();}
}
