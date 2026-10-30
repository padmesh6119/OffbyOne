package com.offbyone.arena;
import com.offbyone.model.User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Component
public class SqlRequestLimit {
    private record Window(long start,int count) {}
    private final Map<UUID,Window> windows=new HashMap<>();
    public synchronized void check(User user) {
        long now=System.currentTimeMillis();
        windows.values().removeIf(w->now-w.start()>10000);
        Window w=windows.getOrDefault(user.getId(),new Window(now,0));
        if(w.count()>=8 || windows.size()>10000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many requests. Wait a few seconds.");
        windows.put(user.getId(),new Window(w.start(),w.count()+1));
    }
}
