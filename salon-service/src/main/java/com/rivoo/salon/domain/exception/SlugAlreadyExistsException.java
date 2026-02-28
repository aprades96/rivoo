package com.rivoo.salon.domain.exception;

public class SlugAlreadyExistsException extends RuntimeException {

    public SlugAlreadyExistsException(String slug) {
        super("Slug already exists: " + slug);
    }
}
