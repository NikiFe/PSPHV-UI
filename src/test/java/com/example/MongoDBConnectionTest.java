package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MongoDBConnectionTest {

    @Test
    void testPasswordHashingAndVerification() {
        String rawPassword = "testPassword123";
        String hashedPassword = MongoDBConnection.hashPassword(rawPassword);

        assertNotNull(hashedPassword);
        assertNotEquals(rawPassword, hashedPassword);
        assertTrue(MongoDBConnection.verifyPassword(rawPassword, hashedPassword));
        assertFalse(MongoDBConnection.verifyPassword("wrongPassword", hashedPassword));
    }

    @Test
    void testVerifyPassword_nullInputs() {
        assertFalse(MongoDBConnection.verifyPassword(null, "someHash"));
        assertFalse(MongoDBConnection.verifyPassword("somePassword", null));
        assertFalse(MongoDBConnection.verifyPassword(null, null));
    }
}
