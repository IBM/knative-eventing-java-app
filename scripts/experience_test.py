import os, time, sys, subprocess
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.keys import Keys

# Do an action on the app's landing page
options = Options()
options.add_argument('--headless')
options.add_argument('--no-sandbox')
options.add_argument('--disable-dev-shm-usage')
driver = webdriver.Chrome(options=options)

subprocess.call("kubectl apply -f ping-source.yaml", shell=True)
time.sleep(60)
driver.get(os.environ["APP_URL"] + "/v1/events");  # Open a browser to the app's landing page
time.sleep(3)
# Verify the expected content is present
html = driver.page_source
print("The page content is: {}".format(html))
if "Hello world!" in html:
    print("Experience Test Successful")
else:
    sys.exit("Experience Test Failed")

# Cleanup
print("Deleting pingsource and clearing the Cloudant DB...")
subprocess.call("kubectl delete pingsource cron-pingsource", shell=True)
driver.get(os.environ["APP_URL"] + "/v1/events?deleteAll=true");  # Clears the Cloudant DB
time.sleep(10)
# Verify the event(s) have been cleared
html = driver.page_source
print("The page content is: {}".format(html))