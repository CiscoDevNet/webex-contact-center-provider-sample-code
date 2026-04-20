class VirtualAgentInfo:
    
    def __init__(self, virtual_agent_id, virtual_agent_name, is_default):
        self.virtual_agent_id = virtual_agent_id
        self.virtual_agent_name = virtual_agent_name
        self.is_default = is_default
    def __repr__(self):
        return f"VirtualAgentInfo(id={self.virtual_agent_id}, name={self.virtual_agent_name}, is_default={self.is_default})"   
