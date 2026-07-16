package com.subasta.model;

public record UserData(
        long id,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String login,
        String email,
        String emailAlternativo,
        int habilitado
) {
}
