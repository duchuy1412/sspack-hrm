package vn.sspack.hrm.platform.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RefreshTokenCodecTest {

    final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void generatesUrlSafe256BitTokensThatDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = codec.generate();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void hashIsDeterministicHexSha256AndDiffersFromRaw() {
        String token = codec.generate();

        assertThat(codec.hash(token)).isEqualTo(codec.hash(token)).hasSize(64).matches("[0-9a-f]+");
        assertThat(codec.hash(token)).isNotEqualTo(token);
        assertThat(codec.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
