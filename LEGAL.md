# Legal and data-source notice

Last reviewed: August 3, 2026

This document describes how Military Aircraft Radar (MAR) currently interacts with external data and tracker services. It is provided for transparency and is not legal advice. Service terms and licenses may change; maintainers and distributors remain responsible for reviewing the current official terms.

## ADSB.lol

ADSB.lol is MAR's aircraft-data provider. Its official API documentation states that the API is available to everyone and identifies the API data license as the Open Database License (ODbL) 1.0.

MAR uses ADSB.lol's nearby-aircraft and military endpoints, merges the responses on the device, and does not operate or publish a separate aircraft-data service.

When worldwide squawk notifications are configured, MAR also uses ADSB.lol's documented global squawk endpoint once per minute. The user may store any number of valid four-digit octal codes. MAR combines codes in each request, splits unusually large lists into rate-limited batches, and locally verifies every returned code before notifying.

MAR treats the aircraft information shown in the app as output produced from the ADSB.lol database and includes the following attribution in the application and project documentation:

> Contains information from ADSB.lol, which is made available under the Open Database License (ODbL) 1.0.

- ADSB.lol API and data-license notice: https://www.adsb.lol/docs/open-data/api/
- ODbL 1.0: https://opendatacommons.org/licenses/odbl/1-0/
- ADSB.lol privacy and license information: https://www.adsb.lol/privacy-license/

MAR does not publish a separate aircraft database. A fork that redistributes or publicly uses an adapted database must independently review the ODbL attribution, share-alike, and access requirements.

## Flightradar24

MAR does not request, scrape, embed, copy, cache, or redistribute Flightradar24 data. After an explicit user action, MAR opens a public Flightradar24 URL in the installed Flightradar24 app or a web browser. Flightradar24 then displays its own service under its own terms.

The plain-text name “Flightradar24” is used only to identify the optional external destination. MAR does not use the Flightradar24 logo and does not claim affiliation, partnership, sponsorship, or endorsement.

- Flightradar24 Terms of Service: https://www.flightradar24.com/terms-of-service

Any future use of Flightradar24 content or automated access would require a new legal review and, where applicable, an authorized API agreement.

## Airplanes.live

Android requests Airplanes.live's documented nearby-aircraft API directly. The free plan is limited to 500 requests per day and is described as non-commercial with no service-level agreement. MAR therefore defaults this source to one request every 180 seconds. A separate 1.2-second Business-rate option is disabled by default, carries an explicit warning, and is intended only for users whose account or IP Airplanes.live has authorized for that rate. The request area expands to the documented 250 NM endpoint maximum only while the map tab is visible; this does not change notification eligibility. Data is merged and deduplicated locally and is not republished as a separate database.

Airplanes.live also documents a global squawk endpoint. MAR uses it only when the primary worldwide request fails. On the Free plan, nearby and fallback squawk polling are spaced to remain within the shared 500-request daily allowance; consequently, this fallback can run later than the fixed one-minute primary check. An explicitly authorized Business-rate option permits the fallback to follow the one-minute check schedule.

- API guide: https://airplanes.live/api-guide/
- API plans: https://airplanes.live/api/
- Terms of Use: https://airplanes.live/terms-of-use/
- Privacy notice: https://airplanes.live/privacy/

## ADS-B Exchange

MAR never scrapes ADS-B Exchange. Android can query its official API only after the user supplies their own valid API key; otherwise ADS-B Exchange is used solely as an outbound aircraft-page destination. Responses are cached briefly on-device for the live map and alerts and are not published as a separate database.

With a configured official key, worldwide squawk notifications can use ADS-B Exchange's documented `sqk` endpoint as a fallback after a failed ADSB.lol request.

The full plain-text name “ADS-B Exchange” is used only to identify the optional external destination. MAR does not use the ADS-B Exchange logo and does not claim affiliation, partnership, sponsorship, or endorsement.

- ADS-B Exchange / JETNET Terms of Use: https://www.jetnet.com/legal/terms-of-use
- ADS-B Exchange data-use policy: https://support.adsbexchange.com/hc/en-us/articles/37364077703693-What-is-ADS-B-Exchange-s-data-use-policy
- ADS-B Exchange media and attribution guidance: https://www.adsbexchange.com/about/media-kit/

ADS-B Exchange attribution and data-use requirements apply to authenticated API use and any republication. Users and distributors are responsible for using a key and plan that authorize their usage.

## OpenStreetMap and Leaflet

The Android map loads Leaflet 1.9.4 from unpkg and requests standard OpenStreetMap tiles only while the map is visible. It keeps visible OpenStreetMap attribution, uses normal WebView caching, identifies the application in its user agent, and does not bulk-download or prefetch tiles.

- OpenStreetMap tile usage policy: https://operations.osmfoundation.org/policies/tiles/
- Leaflet: https://leafletjs.com/
- Leaflet BSD 2-Clause License: https://github.com/Leaflet/Leaflet/blob/main/LICENSE

## Aircraft photos and registration metadata

When a user selects an aircraft that has a registration, MAR requests a registration-specific photo result from the public Planespotters.net photo endpoint and may query the matching Planespotting.be registration page as a fallback and metadata supplement. A returned thumbnail is displayed remotely in the infoblock with its source and photographer credit; it is not bundled with or republished by MAR. Verified registration-bound fields such as operator, aircraft type, MSN, and status may fill otherwise missing live-data fields, but never replace an existing value. General web or Wikimedia fallback images are not treated as aircraft-metadata sources. External data can be stale, incomplete, or incorrect. MAR performs no bulk photo or fleet-database download. Their names, photographs, databases, and trademarks remain the property of their respective owners. No affiliation, sponsorship, or endorsement is claimed.

- Planespotters.net: https://www.planespotters.net/
- Planespotting.be: https://www.planespotting.be/

## Map design and local features

MAR's map controls, filters, tracks, bookmarks, measurement tools, and information panels are original project code implemented with Leaflet. Reference screenshots used during development are not part of the source tree, application package, commits, or releases. No Airplanes.live Globe source code, imagery, icons, map tiles, or website assets are included or copied.

## Inferred frequent destinations

For a selected aircraft, MAR can inspect a 30-day window of publicly available Airplanes.live Globe trace files and detect transitions from airborne to ground. Landing coordinates are associated locally with nearby airports from the public-domain OurAirports data set. The displayed cities, airport matches, and counts are estimates, not an official timetable or a complete all-time history. Missing trace days, incomplete reception, touch-and-go landings, and nearby airports can make the result incomplete or incorrect.

- OurAirports data: https://ourairports.com/data/
- OurAirports data dictionary: https://ourairports.com/help/data-dictionary.html

## Trademarks and independence

ADSB.lol, Airplanes.live, Flightradar24, ADS-B Exchange, OpenStreetMap, Leaflet, Planespotters.net, Planespotting.be, Apple, Android, Google, and all other product or service names are the property of their respective owners. Their appearance in MAR is descriptive and does not imply affiliation or endorsement.

## Safety

All aircraft data is provided as-is and may be delayed, incomplete, incorrectly classified, or inaccurate. Military detection combines explicit provider flags with recognizable military callsign prefixes, operators, registrations, descriptions, and military-specific type codes. Rotorcraft detection uses provider category and description fields. These inferences can produce false positives or miss targets when identifying data is absent. MAR must not be used for navigation, flight safety, air traffic control, flight planning, law enforcement, or operational decisions.
