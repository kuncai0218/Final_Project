Final Project - Android Tourism Map Application
This Android application integrates Esri's ArcGIS Runtime SDK for Android, along with a custom backend running on AWS and backed by a PostgreSQL database. The application displays tourist attractions on a map, allows users to add new attractions, and provides a review system for these attractions. By interacting with an AWS API Gateway endpoint, user-generated content (attractions and reviews) is persisted in a PostgreSQL database.

Key Features
GIS and Mapping

Uses the Esri ArcGIS Runtime SDK for Android to display a fully interactive map.
Shows tourist attractions from an OSM (OpenStreetMap) tourism dataset as a FeatureLayer.
AWS & Backend Integration

Interacts with a backend API hosted on AWS (e.g., AWS Lambda behind an AWS API Gateway).
The apiUrl points to an AWS API Gateway endpoint which provides RESTful operations on attractions and reviews.
New attractions and reviews are inserted, fetched, and stored via the backend, which communicates with a PostgreSQL database.
PostgreSQL Database

The backend service (not part of this Android code, but integrated through the API) uses PostgreSQL as the data store.
Newly added attractions and reviews are persisted in PostgreSQL tables.
Existing data is retrieved and returned as JSON to the client application.
Location Services

Requests and uses the device's location to display the user's current position.
Provides controls for panning, zooming, and recentering the map on the user's location.
Address Search & Geocoding

Integrates with the ArcGIS World Geocoding service for address search and suggestions.
Allows the user to zoom the map to any searched address.
Custom Attractions & Reviews

Enters "Add Attraction" mode to let users tap the map and add a custom point of interest.
Pop-up callouts display detailed attraction info, along with a list of reviews fetched from the backend.
Users can submit new reviews, which are then stored in the PostgreSQL database via the AWS backend.
Architecture Overview
scss
Copy code
          +--------------------------+
          |        Android App       |
          |  (This Project's Client) |
          +------------+-------------+
                       |
                       | HTTPS (REST API)
                       v
          +--------------------------+
          |      AWS API Gateway     |
          +------------+-------------+
                       | Lambda Calls
                       v
          +--------------------------+
          |       AWS Lambda         |
          |  (Backend Logic Layer)   |
          +------------+-------------+
                       | SQL Queries
                       v
          +--------------------------+
          |       PostgreSQL DB      |
          +--------------------------+
Android Client (This Project): Handles UI, map interactions, user input, and calling the backend API.
AWS API Gateway: Provides a RESTful interface to the backend. The apiUrl in the code points to this gateway.
AWS Lambda: Contains custom logic to process requests from the API Gateway, interact with the PostgreSQL database, and return JSON responses.
PostgreSQL Database: Stores attractions and reviews. Insertions, updates, and queries are performed by the Lambda functions.
Technologies Used
Android/Kotlin: For app development and UI.
ArcGIS Runtime SDK for Android: For map display and GIS capabilities.
AWS API Gateway & Lambda: For serverless backend endpoints and logic execution.
PostgreSQL: As the persistent datastore for attractions and reviews.
OkHttp & Gson: For network calls to the backend API and JSON parsing.
Getting Started
Prerequisites
Android Studio: For building and running the app.
ArcGIS Developer Account & API Key: Required to access ArcGIS services.
AWS Setup:
An AWS API Gateway endpoint that triggers AWS Lambda functions.
AWS Lambda functions implementing the logic for attractions and reviews.
A PostgreSQL database (e.g., Amazon RDS for PostgreSQL) accessible by the Lambda functions.
The detailed setup of AWS and PostgreSQL is outside the scope of this Android project, but you must have these services deployed and running with the correct endpoints.
Setup in the Android Project
ArcGIS API Key:
Replace the placeholder in MainActivity.onCreate():

kotlin
Copy code
ArcGISRuntimeEnvironment.setApiKey("YOUR_API_KEY_HERE")
Backend API URL: Update the apiUrl in MainActivity to point to your AWS API Gateway endpoint:

kotlin
Copy code
private val apiUrl = "https://your-api-gateway-url.com/newstage"
Permissions: In AndroidManifest.xml:

xml
Copy code
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
Runtime permissions are requested by the app itself.

Running the Application
Open in Android Studio.
Run on a device or emulator.
Grant location permissions when prompted.
Use the search bar to find places, the location button to recenter, and zoom buttons for navigation.
Activate "Add Attraction" mode and tap on the map to add a custom attraction. This sends a POST request to your backend, which stores data in the PostgreSQL database.
Tap existing attractions (from OSM or custom) to view details and fetch their reviews from the backend database.
Add new reviews; these are posted to the AWS backend and stored in PostgreSQL.
Backend API Contract
GET /attractions: Returns a list of all custom attractions (stored in PostgreSQL).
GET /attractions/{attraction_id}: Returns details for a specific attraction. Checks if it exists in PostgreSQL.
POST /attractions: Inserts a new attraction into PostgreSQL.
GET /attractions/{attraction_id}/reviews: Fetches all reviews for the given attraction from PostgreSQL.
POST /attractions/{attraction_id}/reviews: Inserts a new review into PostgreSQL.
Data Models:

Attraction: { "attraction_id": String, "name": String, "description": String, "tourism": String, "website": String, "lat": Double, "lon": Double }
ReviewResponse: { "review_id": String, "rating": Int, "comment": String }
Ensure the AWS Lambda functions connected to PostgreSQL return data in these formats.

Customization
Basemap and Layers: Change the basemap or add other layers as desired.
Symbol Styles: Adjust markers or other symbology for custom attractions.
Backend Endpoints: Extend the backend to provide more information or add new functionalities.
Troubleshooting
Map not loading: Check your ArcGIS API Key and network connection.
Attractions not appearing: Ensure your AWS API Gateway and Lambda functions are correct, and the PostgreSQL database is reachable.
Reviews not updating: Check the Lambda code and logs in AWS CloudWatch to ensure database connections and queries are functioning properly.
License
This project uses the ArcGIS Runtime SDK for Android. Review the Esri licensing and attribution requirements and any other third-party licenses applicable to your dependencies.

This updated README now reflects the integration with AWS API Gateway, Lambda, and a PostgreSQL database as part of the backend infrastructure supporting the Android application.
