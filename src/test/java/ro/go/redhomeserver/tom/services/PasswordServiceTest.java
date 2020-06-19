package ro.go.redhomeserver.tom.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ro.go.redhomeserver.tom.emails.ResetPasswordEmail;
import ro.go.redhomeserver.tom.exceptions.*;
import ro.go.redhomeserver.tom.models.Account;
import ro.go.redhomeserver.tom.models.ResetPasswordRequest;
import ro.go.redhomeserver.tom.repositories.AccountRepository;
import ro.go.redhomeserver.tom.repositories.ResetPasswordRequestRepository;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PasswordServiceTest {

    @Mock
    private EmailService emailService;
    @Mock
    private ResetPasswordRequestRepository resetPasswordRequestRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private PasswordService passwordService;

    //validatePassword
    @Test
    void should_ThrowPasswordVerificationException_DifferentPasswords() {
        Throwable throwable = catchThrowable(() -> passwordService.validatePassword("1", "2"));
        assertThat(throwable).isInstanceOf(PasswordVerificationException.class);
    }

    @Test
    void should_ThrowWeakPasswordException_ShortPassword() {
        Throwable throwable = catchThrowable(() -> passwordService.validatePassword("arosu", "arosu"));
        assertThat(throwable).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void strongestPasswordsShouldBe8() {
        int score = 0;
        try {
            score = passwordService.validatePassword("Pdsfadsdfsafsaf!1", "Pdsfadsdfsafsaf!1");
        } catch (Exception e) {
            fail("Exception interfered!");
        }
        assertThat(score == 8).isTrue();
    }

    //updateAccountPasswordById
    @Test
    void should_ThrowUserNotFoundException_EmptyUsername() {
        Throwable throwable = catchThrowable(() -> passwordService.updateAccountPasswordById(null, null));
        assertThat(throwable).isInstanceOf(SignUpException.class);
    }

    @Test
    void userShouldHaveHisPasswordChangedAndAllResetRequestsShouldBeDeleted() {
        Account account = new Account();
        ArrayList<ResetPasswordRequest> resetPasswordRequests = new ArrayList<>();
        ResetPasswordRequest r1 = new ResetPasswordRequest();
        ResetPasswordRequest r2 = new ResetPasswordRequest();
        ResetPasswordRequest r3 = new ResetPasswordRequest();
        r1.setAccount(account);
        r2.setAccount(account);
        r3.setAccount(new Account());
        resetPasswordRequests.add(r1);
        resetPasswordRequests.add(r2);
        when(accountRepository.findById(anyString())).thenReturn(java.util.Optional.of(account));
        when(accountRepository.save(any(Account.class))).then(invocation -> invocation.getArguments()[0]);
        doAnswer(invocation -> resetPasswordRequests.removeIf(i -> i.equals(invocation.getArguments()[0]))).when(resetPasswordRequestRepository).deleteAllByAccount(any(Account.class));
        when(passwordEncoder.encode(anyString())).thenReturn("password");

        Account result = null;
        try {
            result = passwordService.updateAccountPasswordById("", "");
        } catch (SignUpException e) {
            fail("Exception interfered!");
        }

        assertThat(result).isNotNull();
        assertThat(result.getPassword().equals("password"));
        assertThat(resetPasswordRequests.size()==1);
        assertThat(resetPasswordRequests.contains(r2)).isTrue();
    }

    //identifyAccountUsingToken
    @Test
    void should_ThrowInvalidTokenException_ResetRequestMissing() {
        Throwable throwable = catchThrowable(() -> passwordService.identifyAccountUsingToken(""));
        assertThat(throwable).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void identifyAccountUsingToken_AccountId_ResetRequestWithAccount() {
        Account account = new Account();
        account.setId("id");
        ResetPasswordRequest rpr = new ResetPasswordRequest();
        rpr.setAccount(account);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, +5);
        rpr.setExpirationDate(calendar.getTime());
        when(resetPasswordRequestRepository.findByToken(anyString())).thenReturn(java.util.Optional.of(rpr));
        try {
            assertThat(passwordService.identifyAccountUsingToken("").equals("id"));
        } catch (Exception e) {
            fail("Exception interfered!");
        }
    }

    //addResetRequest
    @Test
    void should_ThrowUserNotFoundException_NullUsername() {
        Throwable throwable = catchThrowable(() -> passwordService.addResetRequest("", ""));
        assertThat(throwable).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void theResetNeedsToBeSavedWithExpirationDateInOneHourAndAnEmailWillBeSent() {
        Account account = new Account();
        when(accountRepository.findByUsername(anyString())).thenReturn(Optional.of(account));
        doAnswer(invocation -> invocation.getArguments()[0]).when(resetPasswordRequestRepository).save(any(ResetPasswordRequest.class));

        try {
            ResetPasswordRequest rpr = passwordService.addResetRequest("", "");
            verify(emailService, times(1)).sendEmail(any(ResetPasswordEmail.class));
            Calendar calendar = Calendar.getInstance();
            long nowHour = calendar.getTimeInMillis();
            calendar.setTime(rpr.getExpirationDate());
            long expirationHour = calendar.getTimeInMillis();
            long diff = TimeUnit.HOURS.convert(expirationHour-nowHour, TimeUnit.MILLISECONDS);
            assertThat(diff==1);
            assertThat(rpr.getAccount().equals(account));
        } catch (Exception e) {
            fail("Exception interfered!");
        }
    }
}