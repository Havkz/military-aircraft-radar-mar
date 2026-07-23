# Legal and data-source notice

Last reviewed: July 23, 2026

This document describes how Military Aircraft Radar (MAR) currently interacts with external data and tracker services. It is provided for transparency and is not legal advice. Service terms and licenses may change; maintainers and distributors remain responsible for reviewing the current official terms.

## ADSB.lol

ADSB.lol is MAR's aircraft-data provider. Its official API documentation states that the API is available to everyone and identifies the API data license as the Open Database License (ODbL) 1.0.

MAR uses ADSB.lol's nearby-aircraft and military endpoints, merges the responses on the device, and does not operate or publish a separate aircraft-data service.

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

## ADS-B Exchange

MAR does not request, scrape, embed, copy, cache, or redistribute ADS-B Exchange data. After an explicit user action, MAR opens an aircraft page in the ADS-B Exchange installed web app or a web browser. ADS-B Exchange then displays its own service under its own terms.

The full plain-text name “ADS-B Exchange” is used only to identify the optional external destination. MAR does not use the ADS-B Exchange logo and does not claim affiliation, partnership, sponsorship, or endorsement.

- ADS-B Exchange / JETNET Terms of Use: https://www.jetnet.com/legal/terms-of-use
- ADS-B Exchange data-use policy: https://support.adsbexchange.com/hc/en-us/articles/37364077703693-What-is-ADS-B-Exchange-s-data-use-policy
- ADS-B Exchange media and attribution guidance: https://www.adsbexchange.com/about/media-kit/

ADS-B Exchange attribution is required if its data, maps, or screenshots are republished. MAR currently republishes none of those materials; ADS-B Exchange is an outbound destination only.

## Trademarks and independence

ADSB.lol, Flightradar24, ADS-B Exchange, Apple, Android, Google, and all other product or service names are the property of their respective owners. Their appearance in MAR is descriptive and does not imply affiliation or endorsement.

## Safety

All aircraft data is provided as-is and may be delayed, incomplete, incorrectly classified, or inaccurate. MAR must not be used for navigation, flight safety, air traffic control, flight planning, law enforcement, or operational decisions.
