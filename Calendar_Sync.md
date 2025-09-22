# Mt Zion Calendar Sync Implementation

## Overview
Complete calendar integration pipeline for Mount Zion UCC's Alfresco CMS system, implemented to sync calendar events from Alfresco to XTDB with bitemporal capabilities for website display.

## Architecture

### Pipeline Flow
```
Alfresco Calendar Folder → sync_calendar.clj → XTDB → mtzuix-calendar-events.edn → home.clj → Website Display
```

### Key Components

1. **Alfresco Calendar Source**
   - Calendar Folder Node ID: `4f6972f5-9d50-4ff3-a972-f59d500ff3f4`
   - Events stored as `.ics` files with custom properties
   - Uses "publish" tags to control visibility

2. **Sync Script**: `sync_calendar.clj`
   - Babashka script for data extraction
   - Creates XTDB documents with bitemporal tracking
   - Filters events by publish tags and upcoming dates

3. **Website Integration**: `src/com/yakread/app/home.clj`
   - Loads events from synced content file
   - Displays published upcoming events

## Implementation Details

### Calendar Event Properties
Events use Alfresco calendar aspect properties:
- `:ia:whatEvent` - Event title
- `:ia:descriptionEvent` - Event description
- `:ia:fromDate` - Start date/time
- `:ia:toDate` - End date/time
- `:ia:whereEvent` - Location

### XTDB Document Structure
```clojure
{:xt/id uuid
 :content/type :mtzuix-calendar-event
 :content/component :calendar-events
 :content/status :published/:draft
 :alfresco/source-node-id "node-id"
 :calendar/title "Event Title"
 :calendar/start-date "2025-09-28T18:30:00.000+0000"
 :calendar/location "Location"
 :calendar/has-publish-tag true
 :calendar/is-upcoming true}
```

### Key Files

#### sync_calendar.clj
- Main sync script using Babashka
- Extracts events from Alfresco Calendar folder
- Creates XTDB documents with bitemporal tracking
- Saves formatted events to `mtzuix-calendar-events.edn`
- Run with: `bb sync_calendar.clj`

#### home.clj - load-alfresco-events function
```clojure
(defn load-alfresco-events
  "Load calendar events from the synced content file"
  [ctx]
  (if (.exists (clojure.java.io/file "mtzuix-calendar-events.edn"))
    (let [all-events (clojure.edn/read-string (slurp "mtzuix-calendar-events.edn"))
          published-events (filter :has-publish-tag all-events)]
      published-events)
    []))
```

#### mtzuix-calendar-events.edn
Generated file containing formatted events ready for website display.

## Configuration

### Alfresco Connection
- Host: `http://admin.mtzcg.com`
- Username: `admin`
- Password: `admin`
- Calendar Node ID: `4f6972f5-9d50-4ff3-a972-f59d500ff3f4`

### Environment Variables
Set before running sync:
```bash
export ALFRESCO_BASE_URL="http://admin.mtzcg.com"
export ALFRESCO_USERNAME="admin"
export ALFRESCO_PASSWORD="admin"
```

## Usage

### Running the Sync
```bash
cd /Users/tombrooke/Code/trust-server/mtzion/yakread
bb sync_calendar.clj
```

### Expected Output
```
📅 Mt Zion Calendar Events Pipeline
   Alfresco Calendar → XTDB → mtzUIX preparation

📅 Extracting Calendar Events from Alfresco...
   Found 2 items in Calendar folder
   Event documents: 2
   Processing: 1758462011749-8540.ics
     Title: Liberty Commons Worship
     Date: 2025-09-28T18:30:00.000+0000
     Has publish tag: true

✅ Calendar sync completed successfully!
📊 Results:
   📅 Calendar events extracted: 2
   💾 XTDB documents created: 2
   📢 Published events: 1
```

### Website Display
Published events automatically appear on the website home page under "Upcoming Events" section.

## Troubleshooting

### Common Issues

1. **No events showing on website**
   - Check if `mtzuix-calendar-events.edn` exists
   - Verify events have `publish` tags in Alfresco
   - Ensure server restart after sync

2. **Alfresco connection errors**
   - Verify Alfresco server is running at `http://admin.mtzcg.com`
   - Check credentials (admin/admin)
   - Confirm Calendar folder node ID is correct

3. **Property extraction issues**
   - Ensure calendar events use proper Alfresco calendar aspect
   - Check property keys are keywords (`:ia:whatEvent` not `"ia:whatEvent"`)

### Testing Connection
Use the test function in calendar.clj:
```clojure
(com.yakread.alfresco.calendar/test-calendar-connection ctx)
```

## Future Enhancements

1. **Automated Sync**: Set up scheduled sync job
2. **Real XTDB Integration**: Replace file-based serving with direct XTDB queries
3. **Past Events**: Use bitemporal queries to show historical events
4. **Event Details Page**: Create individual event display pages
5. **Calendar View**: Add monthly/weekly calendar display

## Dependencies

- Babashka for sync scripts
- Biff web framework
- XTDB for bitemporal storage
- Alfresco CMS with calendar aspect
- Cheshire for JSON parsing
- clojure.edn for data serialization

## Success Metrics

- ✅ Calendar events extract successfully from Alfresco
- ✅ Events with publish tags appear on website
- ✅ Upcoming events filter correctly
- ✅ XTDB documents created with proper structure
- ✅ Pipeline runs without errors

## Implementation Status: COMPLETE
Last updated: 2025-09-21
User confirmation: "perfect WE did it"