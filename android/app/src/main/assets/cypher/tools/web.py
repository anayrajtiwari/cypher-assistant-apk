"""
Web & Search Tools for Cypher
Uses DuckDuckGo search & in-memory page parsing.
No raw HTML or search history is saved to disk, saving SSD life & memory.
"""

import warnings
warnings.filterwarnings("ignore", category=RuntimeWarning)
from duckduckgo_search import DDGS
import requests
from bs4 import BeautifulSoup
from cypher.tools.base import Tool

def web_search(query: str, max_results: int = 5) -> str:
    """Perform real-time search on DuckDuckGo."""
    try:
        results = list(DDGS().text(query, max_results=max_results))
        if not results:
            return f"No web search results found for: '{query}'"
        
        formatted = []
        for i, res in enumerate(results, 1):
            formatted.append(f"{i}. [{res.get('title')}]({res.get('href')})\n   {res.get('body')}")
        return "\n\n".join(formatted)
    except Exception as e:
        return f"Error executing DuckDuckGo search: {str(e)}"

def read_webpage(url: str, max_chars: int = 4000) -> str:
    """Fetch text content of a webpage in memory without saving to disk."""
    try:
        headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        resp = requests.get(url, headers=headers, timeout=10)
        resp.raise_for_status()

        soup = BeautifulSoup(resp.text, 'html.parser')
        
        # Remove script and style tags
        for script in soup(["script", "style", "nav", "footer", "header"]):
            script.extract()

        text = soup.get_text(separator=' ', strip=True)
        if len(text) > max_chars:
            text = text[:max_chars] + f"\n... [Truncated after {max_chars} characters]"
        return f"URL Content ({url}):\n\n{text}"
    except Exception as e:
        return f"Error fetching webpage '{url}': {str(e)}"

def register_web_tools(registry):
    registry.register(Tool("web_search", "Search DuckDuckGo for live/realtime information", {"type": "object", "properties": {"query": {"type": "string"}, "max_results": {"type": "integer", "default": 5}}, "required": ["query"]}, web_search))
    registry.register(Tool("read_webpage", "Fetch text content of a web page in-memory", {"type": "object", "properties": {"url": {"type": "string"}, "max_chars": {"type": "integer", "default": 4000}}, "required": ["url"]}, read_webpage))
