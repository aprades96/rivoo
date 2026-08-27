package com.rivoo.salon.domain.exception;

import com.rivoo.common.exception.RivooException;
import org.springframework.http.HttpStatus;

public class SlugAlreadyExistsException extends RivooException {

    public SlugAlreadyExistsException(String slug) {
        super("Slug already exists: " + slug, "slug-already-exists", "Slug Already Exists", HttpStatus.CONFLICT);
    }
}
