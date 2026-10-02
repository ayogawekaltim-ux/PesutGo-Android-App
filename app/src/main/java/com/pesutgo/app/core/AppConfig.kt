package com.pesutgo.app.core

object AppConfig {
    const val BASE_URL = "https://www.pesutgo.com"
    const val PROVIDER_LOGIN_URL = "$BASE_URL/login?role=provider&redirect=%2Fprovider%2Fkyc"
    const val CUSTOMER_LOGIN_URL = "$BASE_URL/login?role=customer&redirect=%2Fcustomer%2Forder"
    const val CUSTOMER_PROFILE_URL = "$BASE_URL/customer/profile"
    const val CUSTOMER_HISTORY_URL = "$BASE_URL/customer/history"
    const val CUSTOMER_ORDER_URL = "$BASE_URL/customer/order"

    const val PREFS = "pesutgo_app"
    const val KEY_LAST_URL = "last_url"
    const val KEY_LAST_ROLE = "last_role"
    const val KEY_WAS_AUTHENTICATED = "was_authenticated"

    const val ROLE_PROVIDER = "provider"
    const val ROLE_CUSTOMER = "customer"
}
