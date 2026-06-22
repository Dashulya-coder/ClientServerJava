package http;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.util.List;
import java.util.Optional;

public class BearerAuth extends Authenticator {

    private final JwtService jwtService;

    public BearerAuth(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Result authenticate(HttpExchange exchange) {
        List<String> headers = exchange.getRequestHeaders().get("Authorization");
        if (headers == null || headers.isEmpty()) {
            return new Failure(401);
        }

        String[] parts = headers.get(0).split(" ");
        if (parts.length != 2 || !parts[0].equals("Bearer")) {
            return new Failure(401);
        }

        Optional<String> username = jwtService.verify(parts[1]);
        if (username.isEmpty()) {
            return new Failure(401);
        }
        return new Success(new HttpPrincipal(username.get(), "products"));
    }
}
