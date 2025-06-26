# TilsynsApp

TilsynsApp is an internal Android application developed for Teknik og Miljø, Aarhus Kommune. It streamlines the handling of tilladelser (permits), henstillinger (site notices), and fakturering (invoicing) directly from the field, as well as giving inspectors access to trigger automations such as RegelRytteren, which runs a route optimization for inspection.

## 🚀 Features

* ✅ Secure email-based login (token flow)
* 📥 View and filter pending site registrations by status:

    * Ny, Til fakturering, Fakturer ikke, Faktureret
* 📝 Edit entries, set Kvadratmeter, Tilladelsestype, and Slutdato
* 🔁 Pull-to-refresh and persistent offline cache
* 📍 Shows distance from current location (optional)
* 🧾 Send permits to invoicing or mark them as ignored
* 📡 Trigger a new route optimization for inspection
* 🌙 Full dark mode support
* 🔐 API authentication via X-API-Key header

## 📱 Login Flow

* Enter your work email address
* If your email have the required permissions, a login link will be sent
* Once authorized, your API\_KEY is cached securely
* Currently awaiting authorization for Azure AD SAML for central federation of authorization. 

## 📄 Privacy & Security

* All network communication uses HTTPS
* API keys are stored in SharedPreferences and never hardcoded
* Location access is optional and will only be used to compute distance to the site. 
* Data is not shared with third parties
* Data is not sensitive or personally attributable

## 📍 Future implementations

* ✅ Live location tracking to map distance to nearest site.

## 👷 Maintainers

* Jakob Terkelsen (Digital udvikling, Teknik og Miljø, Aarhus Kommune) 
