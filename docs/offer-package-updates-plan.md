# Offer package updates

Branch `feat/offer-package-admin-purchase-feed`

## Plan and progress

- [x] Inspect the existing admin editor, student purchase feed, and original database configuration.
- [x] Let admins enter the offer price and discount badge independently. Verified that the published card shows the exact saved price and percentage.
- [x] Show a live student card beside the package form, including poster, prices, discount, and places. Verified unsaved field changes and a temporary poster upload in Chrome.
- [x] Let admins duplicate an existing package into an editable draft with its poster. Verified the copy is independent and starts with zero purchases and enquiries.
- [x] Show six recent purchases at a time and load the full history automatically on scroll. Frontend tests reached all 300 records in both the inline list and View all.
- [x] Run the automated tests and verify the updated app against the original database.

## Verification completed 24 November 2026

- Maven verification ran 110 tests with zero failures or errors and one skipped opt-in PostgreSQL concurrency test. The 21 offer integration tests passed, including package creation, discount saves, and duplication on isolated data.
- The frontend suite passed all 11 tests, including a 300-record scroll test for each purchase list.
- Added nullable `class_packages.discount_percent` to the original Supabase PostgreSQL database. The updated local app at [Student Offer](http://127.0.0.1:8080/exam/packages) displayed all 3 published packages. Both purchase lists reached all 266 original published purchases in Chrome.
- The original account opened the [Admin Offer editor](http://127.0.0.1:8080/exam/admin/packages/new) and verified live form changes, poster upload preview, and the prefilled duplicate draft before saving. Save and publication checks used isolated data.
