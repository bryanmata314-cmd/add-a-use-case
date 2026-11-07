package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Refuses a signed, unexpired bearer token whose account has since been deactivated or deleted
 * (FR-SEC-active-account).
 *
 * <p>The JWT is stateless, so without this a deactivated user who was already logged in kept working until her
 * token expired; login was the only place the {@code enabled} flag was read. This converter loads the user named
 * by the token's {@code userId} claim on every authenticated request and throws {@link DisabledException} when
 * the account is disabled or gone. The bearer-token filter turns that into the same {@code 401} as a bad token
 * (through {@link CustomBearerTokenAuthenticationEntryPoint}), and the SPA's existing {@code 401} handling logs
 * the user out.
 *
 * <p>It delegates first and returns the delegate's token unchanged, so the principal stays the {@link Jwt} that
 * {@code UserUtils} reads. It never throws anything but an {@code AuthenticationException}: a missing or
 * non-numeric {@code userId} claim is a {@code DisabledException} too, because any other exception would escape
 * the filter chain as a {@code 500}.
 *
 * <p>Not a {@code @Component}: {@code SecurityConfiguration} builds it around its own
 * {@code jwtAuthenticationConverter} bean, so there is exactly one converter of each kind in the context.
 */
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;

    private final UserRepository userRepository;


    public ActiveAccountJwtAuthenticationConverter(JwtAuthenticationConverter delegate, UserRepository userRepository) {
        this.delegate = delegate;
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AbstractAuthenticationToken authentication = this.delegate.convert(jwt);

        Integer userId = readUserId(jwt);
        if (userId == null) {
            throw new DisabledException("The access token names no user.");
        }

        boolean active = this.userRepository.findById(userId)
                .map(PeerEvaluationUser::isEnabled)
                .orElse(false);
        if (!active) {
            throw new DisabledException("This account has been deactivated.");
        }

        return authentication;
    }

    private static Integer readUserId(Jwt jwt) {
        Object claim = jwt.getClaims().get("userId");
        if (claim instanceof Number number) {
            return number.intValue();
        }
        if (claim instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

}
