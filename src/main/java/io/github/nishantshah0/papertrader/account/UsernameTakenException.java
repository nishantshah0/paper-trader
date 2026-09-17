package io.github.nishantshah0.papertrader.account;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("username already taken: " + username);
    }
}
