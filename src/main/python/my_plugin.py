import requests

class MyPlugin:
    def process(self, arg):
        return arg.split();
    def curl(self, url):
        return requests.get(url);