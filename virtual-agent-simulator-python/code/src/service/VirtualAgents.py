import os
from VirtualAgentInfo import VirtualAgentInfo
import json
parent_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
config_dir = os.path.join(parent_dir, 'config/')

class VirtualAgents:

    def __init__(self):
        self.virtual_agent_info = []
        self._load_virtual_agents()

    def get_all_ai_agent(self):
        return self.virtual_agent_info 
    
    def _load_virtual_agents(self):
        with open(f"{config_dir}/virtual_agents.json", 'r') as file:
            data = json.load(file)
        for item in data:
            self.virtual_agent_info.append(VirtualAgentInfo(virtual_agent_id=item['virtual_agent_id'],virtual_agent_name=item['virtual_agent_name'],is_default=item['is_default']))
