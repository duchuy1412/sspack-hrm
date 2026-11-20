package vn.sspack.hrm.org.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** ORG-04: người duyệt cấp 1 là trưởng đơn vị, leo cây khi thiếu, không bao giờ là chính người gửi. */
class ApproverChainTest {

    private static List<Long> list(Long... heads) {
        return Arrays.asList(heads);
    }

    private static Optional<Long> pick(List<Long> heads, long requester, Set<Long> working) {
        return ApproverChain.pick(heads, requester, working);
    }

    @Test void ownHead()                       { assertThat(pick(list(5L, 1L), 9, Set.of(5L, 1L))).contains(5L); }
    @Test void climbsWhenNoHead()              { assertThat(pick(list(null, 1L), 9, Set.of(1L))).contains(1L); }
    @Test void skipsRequesterAsHead()          { assertThat(pick(list(9L, 1L), 9, Set.of(9L, 1L))).contains(1L); }
    @Test void skipsRequesterHeadingSeveralLevels() { assertThat(pick(list(9L, 9L, 1L), 9, Set.of(9L, 1L))).contains(1L); }
    @Test void skipsHeadNotWorking()           { assertThat(pick(list(5L, 1L), 9, Set.of(1L))).contains(1L); }
    @Test void emptyWhenNobody()               { assertThat(pick(list(null, 9L), 9, Set.of(9L))).isEmpty(); }
}
