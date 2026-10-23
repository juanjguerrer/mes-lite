package io.github.juanjguerrer.meslite.common;

public class NotFoundException extends RuntimeException {

    private NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException byId(String entityName, Object id) {
        return new NotFoundException(entityName + " with id " + id + " not found");
    }

    public static NotFoundException byCode(String entityName, Object code) {
        return new NotFoundException(entityName + " with code " + code + " not found");
    }

    public static NotFoundException byLineCodeAndMachineCode(String lineCode, String machineCode) {
        return new NotFoundException("Machine with line code " + lineCode + " and machine code " + machineCode + " not found");
    }

    public static NotFoundException shiftByLineIdAndCode(Long lineId, String shiftCode) {
        return new NotFoundException("Shift with line id " + lineId + " and shift code " + shiftCode + " not found");
    }
}
