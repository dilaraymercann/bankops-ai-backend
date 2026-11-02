package com.bankops.application;

import com.bankops.domain.Role;

/** İsteği yapan kullanıcı (JWT'den okunur). Use case servisleri yetki kararlarını bununla verir. */
public record CurrentUser(Long id, Role role) {
}
