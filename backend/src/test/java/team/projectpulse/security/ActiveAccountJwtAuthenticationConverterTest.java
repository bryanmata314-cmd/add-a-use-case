package team.projectpulse.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtAuthenticationConverterTest {

    @Mock
    UserRepository userRepository;

    ActiveAccountJwtAuthenticationConverter converter;


    @BeforeEach
    void setUp() {
        this.converter = new ActiveAccountJwtAuthenticationConverter(new JwtAuthenticationConverter(), this.userRepository);
    }

    private static Jwt jwtWithUserId(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("j.smith@abc.edu");
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }

    @Test
    void anActiveAccountKeepsTheJwtAsItsPrincipal() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", true, "student")));
        Jwt jwt = jwtWithUserId(4L);

        AbstractAuthenticationToken authentication = this.converter.convert(jwt);

        assertThat(authentication.getPrincipal()).isSameAs(jwt);
    }

    @Test
    void aDeactivatedAccountIsRefused() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", false, "student")));

        assertThatThrownBy(() -> this.converter.convert(jwtWithUserId(4L))).isInstanceOf(DisabledException.class);
    }

    @Test
    void anAccountThatNoLongerExistsIsRefused() {
        given(this.userRepository.findById(99)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.converter.convert(jwtWithUserId(99))).isInstanceOf(DisabledException.class);
    }

    @Test
    void aTokenWithNoUserIdClaimIsADisabledExceptionNotAnotherException() {
        assertThatThrownBy(() -> this.converter.convert(jwtWithUserId(null))).isInstanceOf(DisabledException.class);
        verifyNoInteractions(this.userRepository);
    }

    @Test
    void aNonNumericUserIdClaimIsADisabledException() {
        assertThatThrownBy(() -> this.converter.convert(jwtWithUserId("not-a-number"))).isInstanceOf(DisabledException.class);
        verifyNoInteractions(this.userRepository);
    }

}
