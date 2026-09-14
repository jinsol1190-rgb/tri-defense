class ApiError(Exception):
    def __init__(self, status, code, **details):
        super().__init__(code)
        self.status = status
        self.code = code
        self.details = details
