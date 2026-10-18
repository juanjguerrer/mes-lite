package io.github.juanjguerrer.meslite.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " with id " + id + " not found");
    }
}
