#!/usr/bin/env python3
"""
Webex API Caller Script
Makes calls to:
1. GET https://webexapis.com/v1/applications - to get list of applications
2. GET https://developer.webex.com/api/applications/{appid} - for each application ID
"""

import requests
import json
from typing import List, Dict, Any

# ========================================
# PASTE YOUR TOKEN HERE
# ========================================
API_TOKEN = "MTAzMDM3ZmQtYmQzNi00ODFmLThiYTgtMDIwNGIwMmY0MGM5MTc0NzZiN2MtN2Fi_PF84_ae9802a8-bf77-4039-966b-4947fd9dfc74"  # Replace this with your actual Webex API token

# API endpoints
APPLICATIONS_LIST_URL = "https://webexapis.com/v1/applications?type=serviceApp"
APPLICATION_DETAIL_URL = "https://webexapis.com/v1/applications/{}"

def get_headers() -> Dict[str, str]:
    """Get headers for API requests"""
    return {
        "Authorization": f"Bearer {API_TOKEN}",
        "Content-Type": "application/json"
    }

def get_applications_list() -> List[Dict[str, Any]]:
    """Get list of applications from Webex API"""
    print("\n🔍 Fetching applications list...")
    print(f"URL: {APPLICATIONS_LIST_URL}")
    
    try:
        response = requests.get(APPLICATIONS_LIST_URL, headers=get_headers())
        response.raise_for_status()
        
        data = response.json()
        #Test Auth App Issuer Name
        print(f"✅ Successfully fetched applications list")
        print(f"📊 Response status: {response.status_code}")
        for app in data['items']:
            if app['name'] == 'Test Auth App Issuer Name':
                print(f"Found application: {app['name']} ({app['id']})")
                delete_app(app['id'])
                print(f"Waiting for 10 seconds...")
                sleep(5)
                

        exit(0)       
        
        # Handle different possible response structures
        if isinstance(data, dict):
            applications = data.get('items', data.get('applications', [data] if 'id' in data else []))
        elif isinstance(data, list):
            applications = data
        else:
            applications = []
            
        print(f"📱 Found {len(applications)} applications")
        return applications
        
    except requests.exceptions.RequestException as e:
        print(f"❌ Error fetching applications list: {e}")
        if hasattr(e, 'response') and e.response is not None:
            print(f"Response status: {e.response.status_code}")
            print(f"Response body: {e.response.text}")
        return []

def delete_app(app_id):
     url = APPLICATION_DETAIL_URL.format(app_id)
     print(f"URL: {url}")
     response = requests.delete(url, headers=get_headers())       
     data = response.json()     
     print(f"✅ Successfully deleted application: {app_id}")
     print(f"📊 Response status: {response.status_code}")
     return data

def get_application_details(app_id: str) -> Dict[str, Any]:
    """Get details for a specific application ID"""
    url = APPLICATION_DETAIL_URL.format(app_id)
    print(f"\n🔍 Fetching details for application ID: {app_id}")
    print(f"URL: {url}")
    
    try:
        response = requests.get(url, headers=get_headers())
        response.raise_for_status()
        
        data = response.json()
        return data
        
    except requests.exceptions.RequestException as e:
        print(f"❌ Error fetching details for {app_id}: {e}")
        if hasattr(e, 'response') and e.response is not None:
            print(f"Response status: {e.response.status_code}")
            print(f"Response body: {e.response.text}")
        return {}

def main():
    """Main function to execute the API calls"""
    print("🚀 Starting Webex API calls...")
    
    # Check if token is set
    if API_TOKEN == "YOUR_TOKEN_HERE":
        print("\n⚠️  WARNING: Please replace 'YOUR_TOKEN_HERE' with your actual Webex API token!")
        print("Edit this file and update the API_TOKEN variable.")
        return
    
    # Step 1: Get applications list
    applications = get_applications_list()
    
    if not applications:
        print("\n❌ No applications found or failed to fetch applications list")
        return
    
    # Display summary of applications found
    print("\n📋 Applications Summary:")
    print("-" * 50)
    for i, app in enumerate(applications, 1):
        app_id = app.get('id', 'Unknown')
        app_name = app.get('name', app.get('displayName', 'Unknown'))
        print(f"{i}. ID: {app_id} | Name: {app_name}")
    
    # Step 2: Get details for each application
    print("\n" + "="*60)
    print("📄 FETCHING APPLICATION DETAILS")
    print("="*60)
    
    all_details = []
    
    for i, app in enumerate(applications, 1):
        app_id = app.get('id')
        if not app_id:
            print(f"\n⚠️  Skipping application {i}: No ID found")
            continue
            
        details = get_application_details(app_id)
        if details:
            all_details.append(details)
            
            # Display key information
            print(f"📝 Key details:")
            for key in ['id', 'name', 'displayName', 'description', 'status', 'created', 'lastModified']:
                if key in details:
                    print(f"   {key}: {details[key]}")
        
        print("-" * 40)
    
    # Summary
    print(f"\n🎉 Completed! Successfully fetched details for {len(all_details)} applications")
    
    # Optionally save to JSON file
    try:
        output_file = "webex_applications_data.json"
        with open(output_file, 'w') as f:
            json.dump({
                'applications_list': applications,
                'applications_details': all_details,
                'summary': {
                    'total_applications': len(applications),
                    'successful_details': len(all_details)
                }
            }, f, indent=2)
        print(f"💾 Data saved to {output_file}")
    except Exception as e:
        print(f"⚠️  Could not save to file: {e}")

if __name__ == "__main__":
    main()