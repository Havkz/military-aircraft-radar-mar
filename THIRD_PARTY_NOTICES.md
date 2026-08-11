# Third-party notices

## ADSB.lol aircraft data

MAR contains information from ADSB.lol, which is made available under the Open Database License (ODbL) 1.0.
Optional worldwide squawk notifications use ADSB.lol's documented global squawk endpoint.

- Database and API: https://www.adsb.lol/docs/open-data/api/
- License: https://opendatacommons.org/licenses/odbl/1-0/

The aircraft data is provided as-is and may be incomplete or inaccurate. MAR's MIT License applies to the application source code, not to third-party aircraft data.

## Google Material Symbols

The navigation icons `radar`, `map`, `flight`, and `settings` are from Google's Material Design Icons repository and are used under the Apache License 2.0.

- Source: https://github.com/google/material-design-icons
- License: https://github.com/google/material-design-icons/blob/master/LICENSE

The vector path data is embedded locally so the app does not contact Google or load remote assets at runtime.

## Airplanes.live aircraft data

Android retrieves nearby-aircraft data from the documented Airplanes.live API under its published API conditions and rate limits.
The selected-aircraft information panel can also analyze publicly available, day-based Airplanes.live Globe trace files to infer frequent landing airports. Results are cached locally for 24 hours.
Optional worldwide squawk notifications use the documented squawk endpoint. The three-code emergency preset requires one Airplanes.live request per code; Free-plan scheduling shares the published daily allowance with map requests.

- API guide: https://airplanes.live/api-guide/
- Terms of Use: https://airplanes.live/terms-of-use/
- Privacy: https://airplanes.live/privacy/

## OurAirports airport data

MAR includes a compact airport directory derived from the OurAirports open data set. OurAirports publishes this data in the public domain. The directory is used locally to associate an inferred landing position with a nearby airport and city.

- Data: https://ourairports.com/data/
- Data dictionary: https://ourairports.com/help/data-dictionary.html

## Leaflet and OpenStreetMap

The Android map loads Leaflet 1.9.4, released under the BSD 2-Clause License, and displays OpenStreetMap standard tiles with visible attribution.

- Leaflet source and license: https://github.com/Leaflet/Leaflet
- OpenStreetMap copyright: https://www.openstreetmap.org/copyright
- Tile usage policy: https://operations.osmfoundation.org/policies/tiles/

Leaflet is distributed under the BSD 2-Clause License:

Copyright (c) 2010-2026, Volodymyr Agafonkin. Copyright (c) 2010-2011, CloudMade. All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## External aircraft photo services

Planespotters.net, planespotting.be, and ADS-B.nl are independent external services. For a user-selected aircraft, MAR can display a remotely loaded, credited thumbnail and use exact identifier-bound registration/operator/type/description/MSN/status fields only to supplement missing live data. MAR does not bundle their photographs, logos, or website assets, bulk-download their databases, or claim affiliation. Their own terms and rights apply to all returned content.

- Planespotters.net: https://www.planespotters.net/
- Planespotting.be: https://www.planespotting.be/
- ADS-B.nl: https://www.ads-b.nl/

## External tracker names

Flightradar24 and ADS-B Exchange are independent external services. MAR uses their names only to identify optional outbound destinations and does not include their logos. All names and trademarks belong to their respective owners. See [LEGAL.md](LEGAL.md).
