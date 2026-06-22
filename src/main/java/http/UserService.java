package http;

import java.util.HashMap;
import java.util.Map;

public class UserService {

    private final Map<String, String> users = new HashMap<>();

    public UserService() {
        users.put("admin", "password");
    }

    public void addUser(String username, String password) {
        users.put(username, password);
    }

    public boolean isValid(String username, String password) {
        return username != null && password != null
                && password.equals(users.get(username));
    }
}
